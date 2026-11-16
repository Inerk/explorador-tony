import socket, threading, hashlib, plistlib, sys, binascii
from srptools import SRPContext, SRPServerSession, constants
from srptools.utils import int_to_bytes
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey
from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey, X25519PublicKey
from cryptography.hazmat.primitives.ciphers.aead import ChaCha20Poly1305
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives import hashes, serialization

PIN = "5940"
ACC_ID = b"AA:BB:CC:DD:EE:FF"
acc_key = Ed25519PrivateKey.generate()
acc_pub = acc_key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
clients = {}

def hkdf(ikm, salt, info):
    return HKDF(hashes.SHA512(), 32, salt.encode(), info.encode()).derive(ikm)
def nonce(s): return b"\x00"*4 + s.encode()
def tlv_enc(items):
    out=b""
    for t,v in items:
        if not v: out+=bytes([t,0]); continue
        for i in range(0,len(v),255): out+=bytes([t,len(v[i:i+255])])+v[i:i+255]
    return out
def tlv_dec(b):
    d={}; i=0; last=None
    while i+1<len(b):
        t=b[i]; n=b[i+1]; v=b[i+2:i+2+n]
        d[t]= d[t]+v if t==last else v; last=t; i+=2+n
    return d

class Conn:
    def __init__(s, sock): s.sock=sock; s.buf=b""; s.rk=None; s.wk=None; s.rc=0; s.wc=0
    def raw(s,n):
        b=b""
        while len(b)<n:
            x=s.sock.recv(n-len(b))
            if not x: raise EOFError
            b+=x
        return b
    def fill(s):
        if s.rk is None:
            x=s.sock.recv(4096)
            if not x: raise EOFError
            s.buf+=x
        else:
            l=s.raw(2); n=int.from_bytes(l,"little"); ct=s.raw(n+16)
            s.buf+=ChaCha20Poly1305(s.rk).decrypt(b"\0"*4+s.rc.to_bytes(8,"little"), ct, l); s.rc+=1
    def read_req(s):
        while b"\r\n\r\n" not in s.buf: s.fill()
        head,s.buf=s.buf.split(b"\r\n\r\n",1)
        lines=head.decode().split("\r\n"); m,p,_=lines[0].split(" ")
        h={l.split(":",1)[0].lower():l.split(":",1)[1].strip() for l in lines[1:]}
        n=int(h.get("content-length","0"))
        while len(s.buf)<n: s.fill()
        body,s.buf=s.buf[:n],s.buf[n:]
        return m,p,h,body
    def send(s,code,body=b"",ctype="application/octet-stream"):
        data=f"HTTP/1.1 {code} OK\r\nContent-Type: {ctype}\r\nContent-Length: {len(body)}\r\n\r\n".encode()+body
        if s.wk is None: s.sock.sendall(data); return
        for i in range(0,len(data),1024):
            ch=data[i:i+1024]; l=len(ch).to_bytes(2,"little")
            s.sock.sendall(l+ChaCha20Poly1305(s.wk).encrypt(b"\0"*4+s.wc.to_bytes(8,"little"),ch,l)); s.wc+=1

def handle(sock):
    c=Conn(sock); st={}; pos=[0.0]
    try:
        while True:
            m,p,h,body=c.read_req()
            if p=="/pair-pin-start": print("PIN shown:",PIN); c.send(200); continue
            if p=="/pair-setup":
                assert h.get("x-apple-hkp")=="3", "missing HKP header"
                t=tlv_dec(body); state=t[6][0]
                if state==1:
                    ctx=SRPContext("Pair-Setup",PIN,prime=constants.PRIME_3072,generator=constants.PRIME_3072_GEN,hash_func=hashlib.sha512)
                    _,verifier,salt=ctx.get_user_data_triplet()
                    sess=SRPServerSession(SRPContext("Pair-Setup",prime=constants.PRIME_3072,generator=constants.PRIME_3072_GEN,hash_func=hashlib.sha512),password_verifier=verifier)
                    st["sess"]=sess; st["salt"]=salt
                    c.send(200,tlv_enc([(6,b"\x02"),(2,int_to_bytes(int(salt,16))),(3,int_to_bytes(int(sess.public,16)))]))
                elif state==3:
                    sess=st["sess"]; A=t[3]; M1=t[4]
                    sess.process(binascii.hexlify(A).decode(), st["salt"])
                    if not sess.verify_proof(binascii.hexlify(M1).decode()):
                        print("SRP proof mismatch (wrong pin?)"); c.send(200,tlv_enc([(6,b"\x04"),(7,b"\x02")])); continue
                    print("SRP proof OK")
                    st["K"]=binascii.unhexlify(sess.key)
                    c.send(200,tlv_enc([(6,b"\x04"),(4,binascii.unhexlify(sess.key_proof_hash))]))
                elif state==5:
                    K=st["K"]; key=hkdf(K,"Pair-Setup-Encrypt-Salt","Pair-Setup-Encrypt-Info")
                    sub=tlv_dec(ChaCha20Poly1305(key).decrypt(nonce("PS-Msg05"),t[5],None))
                    x=hkdf(K,"Pair-Setup-Controller-Sign-Salt","Pair-Setup-Controller-Sign-Info")
                    Ed25519PublicKey.from_public_bytes(sub[3]).verify(sub[10], x+sub[1]+sub[3])
                    clients[sub[1]]=sub[3]; print("M5 signature OK, client",sub[1])
                    ax=hkdf(K,"Pair-Setup-Accessory-Sign-Salt","Pair-Setup-Accessory-Sign-Info")
                    inner=tlv_enc([(1,ACC_ID),(3,acc_pub),(10,acc_key.sign(ax+ACC_ID+acc_pub))])
                    c.send(200,tlv_enc([(6,b"\x06"),(5,ChaCha20Poly1305(key).encrypt(nonce("PS-Msg06"),inner,None))]))
                continue
            if p=="/pair-verify":
                t=tlv_dec(body); state=t[6][0]
                if state==1:
                    e=X25519PrivateKey.generate(); epub=e.public_key().public_bytes(serialization.Encoding.Raw,serialization.PublicFormat.Raw)
                    shared=e.exchange(X25519PublicKey.from_public_bytes(t[3])); st.update(shared=shared,epub=epub,cpub=t[3])
                    key=hkdf(shared,"Pair-Verify-Encrypt-Salt","Pair-Verify-Encrypt-Info"); st["vk"]=key
                    inner=tlv_enc([(1,ACC_ID),(10,acc_key.sign(epub+ACC_ID+t[3]))])
                    c.send(200,tlv_enc([(6,b"\x02"),(3,epub),(5,ChaCha20Poly1305(key).encrypt(nonce("PV-Msg02"),inner,None))]))
                else:
                    sub=tlv_dec(ChaCha20Poly1305(st["vk"]).decrypt(nonce("PV-Msg03"),t[5],None))
                    Ed25519PublicKey.from_public_bytes(clients[sub[1]]).verify(sub[10], st["cpub"]+sub[1]+st["epub"])
                    print("verify OK"); c.send(200,tlv_enc([(6,b"\x04")]))
                    c.rk=hkdf(st["shared"],"Control-Salt","Control-Write-Encryption-Key"); c.wk=hkdf(st["shared"],"Control-Salt","Control-Read-Encryption-Key")
                continue
            if p=="/play":
                pl=plistlib.loads(body); print("PLAY",pl); c.send(200); continue
            if p=="/scrub" and m=="GET": c.send(200,f"duration: 42.000000\nposition: {pos[0]:.6f}\n".encode(),"text/parameters"); continue
            if p.startswith("/scrub?position="): pos[0]=float(p.split("=")[1]); c.send(200); continue
            if p.startswith("/rate"): print("RATE",p); c.send(200); continue
            if p=="/stop": print("STOP"); c.send(200); continue
            c.send(404)
    except EOFError: pass
    except Exception as ex: print("SERVER ERROR", repr(ex)); import traceback; traceback.print_exc()
    finally: sock.close()

srv=socket.socket(); srv.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1); srv.bind(("127.0.0.1",7777)); srv.listen()
print("listening",flush=True)
while True:
    s,_=srv.accept(); threading.Thread(target=handle,args=(s,),daemon=True).start()
