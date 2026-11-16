package com.tony.explorador
fun main(args: Array<String>) {
    val port = args[0].toInt()
    val id = HapIdentity.create()
    AirPlayHap.pinStart("127.0.0.1", port)
    val ltpk = AirPlayHap.pairSetup("127.0.0.1", port, "5940", id)
    println("paired, accessory ltpk ${ltpk.size}")
    val c = AirPlayHap.connect("127.0.0.1", port, id)
    AirPlayHap.play(c, "http://192.168.3.31:1234/m/abc/V%C3%ADdeo.mp4", 0.0)
    println("scrub " + AirPlayHap.scrub(c))
    AirPlayHap.seek(c, 12.5); AirPlayHap.rate(c, 0)
    println("scrub " + AirPlayHap.scrub(c))
    AirPlayHap.stop(c)
    try { AirPlayHap.pairSetup("127.0.0.1", port, "1111", id); println("BAD: wrong pin accepted") } catch (e: HapException) { println("wrong pin -> " + e.message) }
    println("ALL OK")
}
