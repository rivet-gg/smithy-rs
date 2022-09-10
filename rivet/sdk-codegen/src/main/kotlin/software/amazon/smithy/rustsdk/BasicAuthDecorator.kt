package software.amazon.smithy.rustsdk

// import software.amazon.smithy.codegen.core.CodegenException
// import software.amazon.smithy.model.node.Node
// import software.amazon.smithy.model.node.ObjectNode
// import software.amazon.smithy.model.node.StringNode
import software.amazon.smithy.model.shapes.OperationShape
// import software.amazon.smithy.rust.codegen.rustlang.CargoDependency
// import software.amazon.smithy.rust.codegen.rustlang.RustModule
// import software.amazon.smithy.rust.codegen.rustlang.RustWriter
import software.amazon.smithy.rust.codegen.rustlang.Writable
// import software.amazon.smithy.rust.codegen.rustlang.asType
import software.amazon.smithy.rust.codegen.rustlang.rust
// import software.amazon.smithy.rust.codegen.rustlang.rustBlockTemplate
import software.amazon.smithy.rust.codegen.rustlang.rustTemplate
// import software.amazon.smithy.rust.codegen.rustlang.withBlock
// import software.amazon.smithy.rust.codegen.rustlang.withBlockTemplate
import software.amazon.smithy.rust.codegen.rustlang.writable
import software.amazon.smithy.rust.codegen.smithy.CodegenContext
// import software.amazon.smithy.rust.codegen.smithy.RuntimeConfig
// import software.amazon.smithy.rust.codegen.smithy.RuntimeType
import software.amazon.smithy.rust.codegen.smithy.customize.OperationCustomization
import software.amazon.smithy.rust.codegen.smithy.customize.OperationSection
import software.amazon.smithy.rust.codegen.smithy.customize.RustCodegenDecorator
// import software.amazon.smithy.rust.codegen.smithy.generators.LibRsCustomization
// import software.amazon.smithy.rust.codegen.smithy.generators.LibRsSection
import software.amazon.smithy.rust.codegen.smithy.generators.config.ConfigCustomization
import software.amazon.smithy.rust.codegen.smithy.generators.config.ServiceConfig
import software.amazon.smithy.rust.codegen.util.toSnakeCase
// import software.amazon.smithy.rust.codegen.util.dq
// import software.amazon.smithy.rust.codegen.util.expectTrait
// import software.amazon.smithy.rust.codegen.util.orNull


class BasicAuthDecorator : RustCodegenDecorator {
    override val name: String = "BasicAuth"
    override val order: Byte = 0

    override fun configCustomizations(
        codegenContext: CodegenContext,
        baseCustomizations: List<ConfigCustomization>
    ): List<ConfigCustomization> {
        return baseCustomizations + BasicAuthConfigCustomization(codegenContext)
    }

    override fun operationCustomizations(
        codegenContext: CodegenContext,
        operation: OperationShape,
        baseCustomizations: List<OperationCustomization>
    ): List<OperationCustomization> {
        return baseCustomizations + BasicAuthResolverFeature()
    }
}

class BasicAuthConfigCustomization(private val codegenContext: CodegenContext) :
    ConfigCustomization() {
    override fun section(section: ServiceConfig): Writable = writable {
        when (section) {
            is ServiceConfig.ConfigStruct -> rust(
                """
                pub(crate) auth: Option<String>,
                """
            )
            is ServiceConfig.ConfigImpl -> emptySection
            is ServiceConfig.BuilderStruct ->
                rust(
                    """
                    pub(crate) auth: Option<String>,
                    """
                )
            ServiceConfig.BuilderImpl ->
                rustTemplate(
                    """
                    /// Sets the bearer token to be used with the Smithy client.
                    pub fn set_bearer_token(mut self, bearer_token: impl std::fmt::Display) -> Self {
                        self.auth = Some(format!("Bearer {}", bearer_token));
                        self
                    }
                    """
                )
            ServiceConfig.BuilderBuild -> {
                rust(
                    """
                    auth: self
                        .auth
                        .or_else(|| std::env::var("RIVET_LOBBY_TOKEN").ok())
                        .or_else(|| std::env::var("RIVET_CLIENT_TOKEN").ok()),
                    """
                )
            }
            else -> emptySection
        }
    }
}

class BasicAuthResolverFeature() : OperationCustomization() {
    override fun section(section: OperationSection): Writable {
        return when (section) {
            is OperationSection.MutateBuilder -> writable {
                rust(
                    """
                    let mut builder = if let Some(auth) = &_config.auth {
                        builder.header(http::header::AUTHORIZATION, auth.clone())
                    }
                    else {
                        builder
                    };
                    """
                )
            }
            else -> emptySection
        }
    }
}
