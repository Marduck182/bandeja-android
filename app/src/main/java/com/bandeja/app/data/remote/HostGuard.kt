package com.bandeja.app.data.remote

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Metadata is fetched from addresses the user (or an app) hands us. Without this guard a crafted link such as
 * `http://192.168.1.1/admin` or `http://localhost:8080` would make the phone send requests into the local network.
 */
object HostGuard {

    fun isPublic(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return false
        return when (address) {
            is Inet4Address -> {
                val b = address.address.map { it.toInt() and 0xFF }
                // 100.64.0.0/10 (carrier-grade NAT), 0.0.0.0/8, 192.0.0.0/24 and 198.18.0.0/15 (benchmarking)
                !(b[0] == 100 && b[1] in 64..127) && b[0] != 0 &&
                    !(b[0] == 192 && b[1] == 0 && b[2] == 0) && !(b[0] == 198 && b[1] in 18..19)
            }
            // fc00::/7 unique-local addresses are not covered by isSiteLocalAddress.
            is Inet6Address -> (address.address[0].toInt() and 0xFE) != 0xFC
            else -> false
        }
    }

    /** True only if the name resolves and *every* address it resolves to is public. */
    fun resolvesToPublicOnly(host: String): Boolean = runCatching {
        InetAddress.getAllByName(host).let { all -> all.isNotEmpty() && all.all(::isPublic) }
    }.getOrDefault(false)
}
