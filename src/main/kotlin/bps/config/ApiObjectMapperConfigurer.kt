package bps.config

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule

/**
 * Thread-safe because underlying jackson instances are thread-safe.
 *
 * To use with Ratpack and SIAM, try this:
 *
 * ```
 *   SiamSerializationModule.register(DefaultConfigDataBuilder.newDefaultObjectMapper()).also { ApiObjectMapperConfigurer.configureObjectMapper(it) })
 * ```
 */
interface ApiObjectMapperConfigurer {

    fun configureObjectMapper(objectMapper: ObjectMapper): Unit {
        objectMapper
            .registerModule(KotlinModule.Builder().build())
            .registerModule(Jdk8Module())
            .registerModule(GuavaModule())
            .registerModule(JavaTimeModule())
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
            // ensuring the default is active
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
            // begin from geoplace
            .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
            // end from geoplace
            .apply {
                factory
                    .enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
                    .enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES)
            }
    }

    companion object : ApiObjectMapperConfigurer

}
