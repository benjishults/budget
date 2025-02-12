package bps.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.nhubbard.konf.Config
import io.github.nhubbard.konf.source.SourceNotFoundException
import io.github.nhubbard.konf.source.yaml.yaml
import java.io.IOException

/**
 *
 * Usage:
 *
 * Assuming you have some number of yaml configuration files where some are meant to take precedence over the others.
 *
 * Suppose the top-level element names in each of those files are `bifrost`, `ratpack`, `protobuf`, and `auth`.
 *
 * ```kotlin
 * object Configurations : ConfigurationHelper(sequenceOf { ... }) {
 *
 *     val bifrost: BifrostConfig =
 *         config
 *             .at("bifrost")
 *             .toValue()
 *
 *     val ratpack: RatpackConfig =
 *         config
 *             .at("ratpack")
 *             .toValue()
 *
 *     val protobuf: ProtobufModule.Config =
 *         config
 *             .at("protobuf")
 *             .toValue()
 *
 *     val checkToken: CheckTokenConfig =
 *         config
 *             .at("auth")
 *             .toValue()
 *
 * }
 *
 * Configurations.bifrost.urls["pitboss"]
 * Configurations.ratpack.port
 * // etc.
 * ```
 *
 * @param filesProducer a [Sequence] of file names.  These can be relative to a resource root or absolute paths.
 * The elements later in the sequence will take precedence over those earlier.
 * @param objectMapperConfigurer configures the [ObjectMapper] used to load the config files
 * @param loadEnv if `true`, environment variables will be loaded with precedence over the file configuration.
 * @param loadSystemProperties if `true`, system properties will be loaded with precedence over everything.
 */
open class ConfigurationHelper(
    filesProducer: Sequence<String>,
    objectMapperConfigurer: (ObjectMapper) -> Unit = ApiObjectMapperConfigurer.Companion::configureObjectMapper,
    loadEnv: Boolean = true,
    loadSystemProperties: Boolean = true,
) {

    val config: Config =
        Config()
            .also { config: Config ->
                objectMapperConfigurer(config.mapper)
            }
            .let {
                filesProducer.fold(it) { config: Config, fileName: String ->
                    try {
                        config.from.yaml.resource(fileName, optional = false)
                    } catch (ex: IOException) {
                        config.from.yaml.file(fileName, optional = true)
                    } catch (ex: SourceNotFoundException) {
                        config.from.yaml.file(fileName, optional = true)
                    }
                }
            }
            .let { config: Config ->
                if (loadEnv)
                    config.from.env()
                else
                    config
            }
            .let { config: Config ->
                if (loadSystemProperties)
                    config.from.systemProperties()
                else
                    config
            }

}
