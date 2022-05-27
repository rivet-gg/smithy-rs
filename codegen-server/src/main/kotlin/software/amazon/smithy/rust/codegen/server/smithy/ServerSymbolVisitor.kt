/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.rust.codegen.smithy

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.ErrorTrait
import software.amazon.smithy.rust.codegen.rustlang.RustType
import software.amazon.smithy.rust.codegen.smithy.traits.SyntheticInputTrait
import software.amazon.smithy.rust.codegen.smithy.traits.SyntheticOutputTrait
import software.amazon.smithy.rust.codegen.util.hasTrait
import software.amazon.smithy.rust.codegen.util.toPascalCase
import software.amazon.smithy.rust.codegen.smithy.protocols.HttpTraitHttpBindingResolver
import software.amazon.smithy.rust.codegen.smithy.protocols.HttpLocation

/**
 * Overrides symbol converter so that it doesn't generate HTTP bound members on structures
 * (besides HttpPayload)
 */
class ServerSymbolVisitor(
    private val model: Model,
    private val serviceShape: ServiceShape?,
    private val config: SymbolVisitorConfig = DefaultConfig
) : SymbolVisitor(model, serviceShape, config) {
    /**
     * Services can rename their contained shapes. See https://awslabs.github.io/smithy/1.0/spec/core/model.html#service
     * specifically, `rename`
     */
    private fun Shape.contextName(): String {
        return if (serviceShape != null) {
            id.getName(serviceShape)
        } else {
            id.name
        }
    }

    override fun structureShape(shape: StructureShape): Symbol {
        val httpIndex = HttpBindingIndex.of(model)

        val isError = shape.hasTrait<ErrorTrait>()
        val isInput = shape.hasTrait<SyntheticInputTrait>()
        val isOutput = shape.hasTrait<SyntheticOutputTrait>()
        val name = shape.contextName().toPascalCase().letIf(isError && config.codegenConfig.renameExceptions) {
            it.replace("Exception", "Error")
        }
        val builder = symbolBuilder(
			when {
				isOutput ->
					StructureShape.Builder()
					.id(shape.getId())
					.members(shape.members().filter { memberShape ->
                        val (headerBindings, prefixHeaderBinding) =
                            httpIndex.getResponseBindings(memberShape, HttpLocation.HEADER) to
                            httpIndex.getResponseBindings(memberShape, HttpLocation.PREFIX_HEADERS).getOrNull(0)

                        !headerBindings.isEmpty() || prefixHeaderBinding != null
                    })
					.build()
				else -> shape
			},
			RustType.Opaque(name)
		)
        return when {
            isError -> builder.locatedIn(Errors)
            isInput -> builder.locatedIn(Inputs)
            isOutput -> builder.locatedIn(Outputs)
            else -> builder.locatedIn(Models)
        }.build()
    }

    private fun symbolBuilder(shape: Shape?, rustType: RustType): Symbol.Builder {
        val builder = Symbol.builder().putProperty(SHAPE_KEY, shape)
        return builder.rustType(rustType)
            .name(rustType.name)
            // Every symbol that actually gets defined somewhere should set a definition file
            // If we ever generate a `thisisabug.rs`, there is a bug in our symbol generation
            .definitionFile("thisisabug.rs")
    }
}

// // TODO(chore): Move this to a useful place
// private const val RUST_TYPE_KEY = "rusttype"
private const val SHAPE_KEY = "shape"
// private const val SYMBOL_DEFAULT = "symboldefault"
// private const val RENAMED_FROM_KEY = "renamedfrom"

// fun Symbol.Builder.rustType(rustType: RustType): Symbol.Builder {
//     return this.putProperty(RUST_TYPE_KEY, rustType)
// }

// fun Symbol.Builder.renamedFrom(name: String): Symbol.Builder {
//     return this.putProperty(RENAMED_FROM_KEY, name)
// }

// fun Symbol.renamedFrom(): String? = this.getProperty(RENAMED_FROM_KEY, String::class.java).orNull()

// fun Symbol.defaultValue(): Default = this.getProperty(SYMBOL_DEFAULT, Default::class.java).orElse(Default.NoDefault)
// fun Symbol.Builder.setDefault(default: Default): Symbol.Builder {
//     return this.putProperty(SYMBOL_DEFAULT, default)
// }

// /**
//  * Type representing the default value for a given type. (eg. for Strings, this is `""`)
//  */
// sealed class Default {
//     /**
//      * This symbol has no default value. If the symbol is not optional, this will be an error during builder construction
//      */
//     object NoDefault : Default()

//     /**
//      * This symbol should use the Rust `std::default::Default` when unset
//      */
//     object RustDefault : Default()
// }

// /**
//  * True when it is valid to use the default/0 value for [this] symbol during construction.
//  */
// fun Symbol.canUseDefault(): Boolean = this.defaultValue() != Default.NoDefault

// /**
//  * True when [this] is will be represented by Option<T> in Rust
//  */
// fun Symbol.isOptional(): Boolean = when (this.rustType()) {
//     is RustType.Option -> true
//     else -> false
// }

// fun Symbol.isRustBoxed(): Boolean = rustType().stripOuter<RustType.Option>() is RustType.Box

// // Symbols should _always_ be created with a Rust type & shape attached
// fun Symbol.rustType(): RustType = this.getProperty(RUST_TYPE_KEY, RustType::class.java).get()
// fun Symbol.shape(): Shape = this.expectProperty(SHAPE_KEY, Shape::class.java)

/**
 * Utility function similar to `let` that conditionally applies [f] only if [cond] is true.
 */
fun <T> T.letIf(cond: Boolean, f: (T) -> T): T {
    return if (cond) {
        f(this)
    } else this
}

