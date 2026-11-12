package com.udenar.runway.data.cuenta

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidacionCuentaTest {

    @Test
    fun correos() {
        assertNull(ValidacionCuenta.correo("laura.g@correo.co"))
        assertNull(ValidacionCuenta.correo("  laura@udenar.edu.co "))
        assertNotNull(ValidacionCuenta.correo("laura@correo"))
        assertNotNull(ValidacionCuenta.correo("sin-arroba.com"))
        assertNotNull(ValidacionCuenta.correo(""))
    }

    @Test
    fun contrasenas() {
        assertNull(ValidacionCuenta.contrasena("corre2026"))
        assertNotNull(ValidacionCuenta.contrasena("abc12")) // corta
        assertNotNull(ValidacionCuenta.contrasena("solamenteletras"))
        assertNotNull(ValidacionCuenta.contrasena("12345678"))
    }

    @Test
    fun nombres() {
        assertNull(ValidacionCuenta.nombre("Laura"))
        assertNotNull(ValidacionCuenta.nombre("   "))
        assertNotNull(ValidacionCuenta.nombre("x".repeat(31)))
    }
}
