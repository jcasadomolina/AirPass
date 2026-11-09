package com.jcasadomolina.airpass

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform