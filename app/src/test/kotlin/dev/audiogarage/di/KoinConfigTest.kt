package dev.audiogarage.di

import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

/**
 * Koin resolves at runtime: this test is the compile-time safety net. It checks
 * that every constructor dependency declared in the modules can be resolved.
 */
@OptIn(KoinExperimentalAPI::class)
class KoinConfigTest {
    @Test
    fun `all app modules resolve their dependencies`() {
        module { includes(appModules) }.verify(
            // verify() inspects constructors, not definition lambdas: primitive values
            // passed explicitly in a lambda (e.g. BuildConfig fields) are not Koin lookups.
            extraTypes = listOf(String::class, Int::class, Boolean::class),
        )
    }
}
