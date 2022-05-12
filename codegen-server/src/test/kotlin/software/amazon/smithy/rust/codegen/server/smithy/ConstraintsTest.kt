/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.server.smithy

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import software.amazon.smithy.model.shapes.ListShape
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.rust.codegen.server.smithy.testutil.serverTestSymbolProvider
import software.amazon.smithy.rust.codegen.smithy.canReachConstrainedShape
import software.amazon.smithy.rust.codegen.smithy.hasConstraintTrait
import software.amazon.smithy.rust.codegen.smithy.isConstrained
import software.amazon.smithy.rust.codegen.smithy.requiresNewtype
import software.amazon.smithy.rust.codegen.testutil.asSmithyModel
import software.amazon.smithy.rust.codegen.util.lookup

class ConstraintsTest {
    private val model =
        """
            namespace test

            service TestService {
                version: "123",
                operations: [TestOperation]
            }
            
            operation TestOperation {
                input: TestInputOutput,
                output: TestInputOutput,
            }
            
            structure TestInputOutput {
                map: MapA,
                
                recursive: RecursiveShape
            }
            
            structure RecursiveShape {
                shape: RecursiveShape,
                mapB: MapB
            }
            
            @length(min: 1, max: 69)
            map MapA {
                key: String,
                value: MapB
            }
            
            map MapB {
                key: String,
                value: StructureA
            }
            
            @uniqueItems
            list ListA {
                member: MyString
            }
            
            @pattern("\\w+")
            string MyString
            
            structure StructureA {
                @range(min: 1, max: 69)
                int: Integer,
                
                @required
                string: String
            }
            
            // This shape is not in the service closure.
            structure StructureB {
                @pattern("\\w+")
                patternString: String,
                
                @required
                requiredString: String,
                
                mapA: MapA,
                
                @length(min: 1, max: 5)
                mapAPrecedence: MapA
            }
            """.asSmithyModel()
    private val symbolProvider = serverTestSymbolProvider(model)

    private val testInputOutput = model.lookup<StructureShape>("test#TestInputOutput")
    private val recursiveShape = model.lookup<StructureShape>("test#RecursiveShape")
    private val mapA = model.lookup<MapShape>("test#MapA")
    private val mapB = model.lookup<MapShape>("test#MapB")
    private val listA = model.lookup<ListShape>("test#ListA")
    private val myString = model.lookup<StringShape>("test#MyString")
    private val structA = model.lookup<StructureShape>("test#StructureA")
    private val structAInt = model.lookup<MemberShape>("test#StructureA\$int")
    private val structAString = model.lookup<MemberShape>("test#StructureA\$string")

    @Test
    fun `it should recognize constraint traits`() {
        listOf(mapA, structAInt, structAString, myString).forEach {
            it.hasConstraintTrait() shouldBe true
        }

        listOf(mapB, structA).forEach {
            it.hasConstraintTrait() shouldBe false
        }
    }

    @Test
    fun `it should not recognize uniqueItems as a constraint trait because it's deprecated`() {
        listA.hasConstraintTrait() shouldBe false
    }

    @Test
    fun `it should detect supported constrained traits as constrained`() {
        structA.isConstrained(symbolProvider) shouldBe true
    }

    @Test
    fun `it should not detect unsupported constrained traits as constrained`() {
        listOf(structAInt, structAString, myString).forEach {
            it.isConstrained(symbolProvider) shouldBe false
        }
    }

    @Test
    fun `it should evaluate reachability of constrained shapes`() {
        mapA.canReachConstrainedShape(model, symbolProvider) shouldBe true
        structAInt.canReachConstrainedShape(model, symbolProvider) shouldBe false

        // This should be true when we start supporting the `pattern` trait on string shapes.
        listA.canReachConstrainedShape(model, symbolProvider) shouldBe false

        // All of these eventually reach `StructureA`, which is constrained because one of its members is `required`.
        testInputOutput.canReachConstrainedShape(model, symbolProvider) shouldBe true
        mapB.canReachConstrainedShape(model, symbolProvider) shouldBe true
        recursiveShape.canReachConstrainedShape(model, symbolProvider) shouldBe true
    }

    @Test
    fun `only some constraint traits on member shapes should warrant a newtype`() {
        structAInt.requiresNewtype() shouldBe true
        structAString.requiresNewtype() shouldBe false

        val structBPatternString = model.lookup<MemberShape>("test#StructureB\$patternString")
        val structBRequiredString = model.lookup<MemberShape>("test#StructureB\$requiredString")
        val structBMapA = model.lookup<MemberShape>("test#StructureB\$mapA")
        val structBMapAPrecedence = model.lookup<MemberShape>("test#StructureB\$mapAPrecedence")

        structBPatternString.requiresNewtype() shouldBe true
        structBRequiredString.requiresNewtype() shouldBe false
        structBMapA.requiresNewtype() shouldBe false
        structBMapAPrecedence.requiresNewtype() shouldBe true
    }
}
