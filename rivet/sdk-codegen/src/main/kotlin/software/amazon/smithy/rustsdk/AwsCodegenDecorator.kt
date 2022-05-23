package software.amazon.smithy.rustsdk

import software.amazon.smithy.rust.codegen.smithy.customizations.DocsRsMetadataDecorator
import software.amazon.smithy.rust.codegen.smithy.customizations.DocsRsMetadataSettings
import software.amazon.smithy.rust.codegen.smithy.customize.CombinedCodegenDecorator

val DECORATORS = listOf(
    BasicAuthDecorator(),

    // Only build docs-rs for linux to reduce load on docs.rs
    DocsRsMetadataDecorator(DocsRsMetadataSettings(targets = listOf("x86_64-unknown-linux-gnu"), allFeatures = true))
)

class RivetCodegenDecorator : CombinedCodegenDecorator(DECORATORS) {
    override val name: String = "RivetCodegenDecorator"
    override val order: Byte = -1
}
