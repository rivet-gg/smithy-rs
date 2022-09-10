package software.amazon.smithy.rustsdk

import java.util.Locale
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.rust.codegen.rustlang.Writable
import software.amazon.smithy.rust.codegen.rustlang.rust
import software.amazon.smithy.rust.codegen.rustlang.rustTemplate
import software.amazon.smithy.rust.codegen.rustlang.writable
import software.amazon.smithy.rust.codegen.smithy.CodegenContext
import software.amazon.smithy.rust.codegen.smithy.customize.OperationCustomization
import software.amazon.smithy.rust.codegen.smithy.customize.OperationSection
import software.amazon.smithy.rust.codegen.smithy.customize.RustCodegenDecorator
import software.amazon.smithy.rust.codegen.smithy.generators.config.ConfigCustomization
import software.amazon.smithy.rust.codegen.smithy.generators.config.ServiceConfig
import software.amazon.smithy.rust.codegen.util.toSnakeCase

class UriDecorator : RustCodegenDecorator {
    override val name: String = "Uri"
    override val order: Byte = 0

    override fun configCustomizations(
        codegenContext: CodegenContext,
        baseCustomizations: List<ConfigCustomization>
    ): List<ConfigCustomization> {
        return baseCustomizations + UriConfigCustomization(codegenContext)
    }

    override fun operationCustomizations(
        codegenContext: CodegenContext,
        operation: OperationShape,
        baseCustomizations: List<OperationCustomization>
    ): List<OperationCustomization> {
        return baseCustomizations + UriResolverFeature()
    }
}

class UriConfigCustomization(private val codegenContext: CodegenContext) :
    ConfigCustomization() {
    override fun section(section: ServiceConfig): Writable = writable {
        when (section) {
            is ServiceConfig.ConfigStruct -> rust(
                """
                pub(crate) uri: String,
                """
            )
            is ServiceConfig.ConfigImpl -> emptySection
            is ServiceConfig.BuilderStruct ->
                rust(
                    """
                    pub(crate) uri: Option<String>,
                    """
                )
            ServiceConfig.BuilderImpl ->
                rustTemplate(
                    """
                    /// Sets the base URI to be used with the Smithy client.
                    pub fn set_uri(mut self, uri: impl ToString) -> Self {
                        self.uri = Some(uri.to_string());
                        self
                    }
                    """
                )
            ServiceConfig.BuilderBuild -> {
                val apiEnvKey = "RIVET_" + codegenContext.serviceShape.id.name
                    .replace("Service", "")
                    .toSnakeCase()
                    .toUpperCase(Locale.US) + "_API_URL"
                val apiUrl = "https://" + codegenContext.serviceShape.id.name
                    .replace("Service", "")
                    .toSnakeCase()
                    .replace("_", "-") + ".api.rivet.gg/v1"

                rust(
                    """
                    uri: self
                        .uri
                        .or_else(|| std::env::var("$apiEnvKey").ok())
                        .unwrap_or_else(|| "$apiUrl".to_string()),
                    """
                )
            }
            else -> emptySection
        }
    }
}

class UriResolverFeature() : OperationCustomization() {
    override fun section(section: OperationSection): Writable {
        return when (section) {
            is OperationSection.MutateUri -> writable {
                rust(
                    """
                    _uri = format!("{}{}", _config.uri.clone(), _uri);
                    """
                )
            }
            else -> emptySection
        }
    }
}
