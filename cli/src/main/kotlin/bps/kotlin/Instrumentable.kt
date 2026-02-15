package bps.kotlin

/**
 * Annotate a class with this if:
 *
 * 1. you do not want it to be extended in your object model but
 * 2. some framework (DI or mocking) needs to be able to extend it
 *
 * Add the following to your `build.gradle` file:
 *
 * ```
 * allOpen {
 *   annotations("bps.kotlin.Instrumentable")
 * }
 * ```
 *
 * The end result is that a compiler plugin makes the class and all its non-private members `open`.
 *
 * In this way, we document we do not intend this class to be extended except by such frameworks.
 */
annotation class Instrumentable
