package org.jetbrains.dokka

import org.jetbrains.dokka.LanguageService.RenderMode

/**
 * Implements [LanguageService] and provides rendering of symbols in Kotlin language
 */
class KotlinLanguageService : LanguageService {
    override fun render(node: DocumentationNode, renderMode: RenderMode): ContentNode {
        return content {
            when (node.kind) {
                DocumentationNode.Kind.Package -> renderPackage(node)
                DocumentationNode.Kind.Class,
                DocumentationNode.Kind.Interface,
                DocumentationNode.Kind.Enum,
                DocumentationNode.Kind.AnnotationClass,
                DocumentationNode.Kind.Object -> renderClass(node)

                DocumentationNode.Kind.EnumItem,
                DocumentationNode.Kind.ExternalClass -> if (renderMode == RenderMode.FULL) identifier(node.name)

                DocumentationNode.Kind.TypeParameter -> renderTypeParameter(node)
                DocumentationNode.Kind.Type,
                DocumentationNode.Kind.UpperBound -> renderType(node)

                DocumentationNode.Kind.Modifier -> renderModifier(node)
                DocumentationNode.Kind.Constructor,
                DocumentationNode.Kind.Function,
                DocumentationNode.Kind.DefaultObjectFunction,
                DocumentationNode.Kind.PropertyAccessor -> renderFunction(node)
                DocumentationNode.Kind.Property,
                DocumentationNode.Kind.DefaultObjectProperty -> renderProperty(node)
                else -> identifier(node.name)
            }
        }
    }

    override fun renderName(node: DocumentationNode): String {
        return when (node.kind) {
            DocumentationNode.Kind.Constructor -> node.owner!!.name
            else -> node.name
        }
    }

    private fun ContentBlock.renderPackage(node: DocumentationNode) {
        keyword("package")
        text(" ")
        identifier(node.name)
    }

    private fun ContentBlock.renderList(nodes: List<DocumentationNode>, separator: String = ", ", renderItem: (DocumentationNode) -> Unit) {
        if (nodes.none())
            return
        renderItem(nodes.first())
        nodes.drop(1).forEach {
            symbol(separator)
            renderItem(it)
        }
    }

    private fun ContentBlock.renderLinked(node: DocumentationNode, body: ContentBlock.(DocumentationNode)->Unit) {
        val to = node.links.firstOrNull()
        if (to == null)
            body(node)
        else
            link(to) {
                body(node)
            }
    }

    private fun ContentBlock.renderType(node: DocumentationNode) {
        val typeArguments = node.details(DocumentationNode.Kind.Type)
        if (node.name == "Function${typeArguments.count() - 1}") {
            // lambda
            symbol("(")
            renderList(typeArguments.take(typeArguments.size - 1)) {
                renderType(it)
            }
            symbol(")")
            text(" ")
            symbol("->")
            text(" ")
            renderType(typeArguments.last())
            return
        }
        if (node.name == "ExtensionFunction${typeArguments.count() - 2}") {
            // extension lambda
            renderType(typeArguments.first())
            symbol(".")
            symbol("(")
            renderList(typeArguments.drop(1).take(typeArguments.size - 2)) {
                renderType(it)
            }
            symbol(")")
            text(" ")
            symbol("->")
            text(" ")
            renderType(typeArguments.last())
            return
        }
        renderLinked(node) { identifier(it.name) }
        if (typeArguments.any()) {
            symbol("<")
            renderList(typeArguments) {
                renderType(it)
            }
            symbol(">")
        }
    }

    private fun ContentBlock.renderModifier(node: DocumentationNode) {
        when (node.name) {
            "final", "internal", "var" -> {}
            else -> {
                keyword(node.name)
                text(" ")
            }
        }
    }

    private fun ContentBlock.renderTypeParameter(node: DocumentationNode) {
        val constraints = node.details(DocumentationNode.Kind.UpperBound)
        identifier(node.name)
        if (constraints.any()) {
            symbol(" : ")
            renderList(constraints) {
                renderType(it)
            }
        }
    }

    private fun ContentBlock.renderParameter(node: DocumentationNode) {
        renderAnnotationsForNode(node)
        identifier(node.name)
        symbol(": ")
        val parameterType = node.detail(DocumentationNode.Kind.Type)
        renderType(parameterType)
        val valueNode = node.details(DocumentationNode.Kind.Value).firstOrNull()
        if (valueNode != null) {
            symbol(" = ")
            text(valueNode.name)
        }
    }

    private fun ContentBlock.renderTypeParametersForNode(node: DocumentationNode) {
        val typeParameters = node.details(DocumentationNode.Kind.TypeParameter)
        if (typeParameters.any()) {
            symbol("<")
            renderList(typeParameters) {
                renderTypeParameter(it)
            }
            symbol("> ")
        }
    }

    private fun ContentBlock.renderSupertypesForNode(node: DocumentationNode) {
        val supertypes = node.details(DocumentationNode.Kind.Supertype)
        if (supertypes.any()) {
            symbol(" : ")
            renderList(supertypes) {
                renderType(it)
            }
        }
    }

    private fun ContentBlock.renderModifiersForNode(node: DocumentationNode) {
        val modifiers = node.details(DocumentationNode.Kind.Modifier)
        for (it in modifiers) {
            if (node.kind == org.jetbrains.dokka.DocumentationNode.Kind.Interface && it.name == "abstract")
                continue
            renderModifier(it)
        }
    }

    private fun ContentBlock.renderAnnotationsForNode(node: DocumentationNode) {
        node.annotations.forEach {
            renderAnnotation(it)
        }
    }

    private fun ContentBlock.renderAnnotation(node: DocumentationNode) {
        identifier(node.name)
        val parameters = node.details(DocumentationNode.Kind.Parameter)
        if (!parameters.isEmpty()) {
            symbol("(")
            renderList(parameters) {
                text(it.detail(DocumentationNode.Kind.Value).name)
            }
            symbol(")")
        }
        text(" ")
    }

    private fun ContentBlock.renderClass(node: DocumentationNode) {
        renderModifiersForNode(node)
        renderAnnotationsForNode(node)
        when (node.kind) {
            DocumentationNode.Kind.Class -> keyword("class ")
            DocumentationNode.Kind.Interface -> keyword("trait ")
            DocumentationNode.Kind.Enum -> keyword("enum class ")
            DocumentationNode.Kind.AnnotationClass -> keyword("annotation class ")
            DocumentationNode.Kind.EnumItem -> keyword("enum val ")
            DocumentationNode.Kind.Object -> keyword("object ")
            else -> throw IllegalArgumentException("Node $node is not a class-like object")
        }

        identifierOrDeprecated(node)
        renderTypeParametersForNode(node)
        renderSupertypesForNode(node)
    }

    private fun ContentBlock.renderFunction(node: DocumentationNode) {
        renderModifiersForNode(node)
        renderAnnotationsForNode(node)
        when (node.kind) {
            DocumentationNode.Kind.Constructor -> identifier(node.owner!!.name)
            DocumentationNode.Kind.Function,
            DocumentationNode.Kind.DefaultObjectFunction -> keyword("fun ")
            DocumentationNode.Kind.PropertyAccessor -> {}
            else -> throw IllegalArgumentException("Node $node is not a function-like object")
        }
        renderTypeParametersForNode(node)
        val receiver = node.details(DocumentationNode.Kind.Receiver).singleOrNull()
        if (receiver != null) {
            renderType(receiver.detail(DocumentationNode.Kind.Type))
            symbol(".")
        }

        if (node.kind != org.jetbrains.dokka.DocumentationNode.Kind.Constructor)
            identifierOrDeprecated(node)

        symbol("(")
        renderList(node.details(DocumentationNode.Kind.Parameter)) {
            renderParameter(it)
        }
        symbol(")")
        if (needReturnType(node)) {
            symbol(": ")
            renderType(node.detail(DocumentationNode.Kind.Type))
        }
    }

    private fun needReturnType(node: DocumentationNode) = when(node.kind) {
        DocumentationNode.Kind.Constructor -> false
        DocumentationNode.Kind.PropertyAccessor -> node.name == "get"
        else -> true
    }

    private fun ContentBlock.renderProperty(node: DocumentationNode) {
        renderModifiersForNode(node)
        renderAnnotationsForNode(node)
        when (node.kind) {
            DocumentationNode.Kind.Property,
            DocumentationNode.Kind.DefaultObjectProperty -> keyword("${node.getPropertyKeyword()} ")
            else -> throw IllegalArgumentException("Node $node is not a property")
        }
        renderTypeParametersForNode(node)
        val receiver = node.details(DocumentationNode.Kind.Receiver).singleOrNull()
        if (receiver != null) {
            renderType(receiver.detail(DocumentationNode.Kind.Type))
            symbol(".")
        }

        identifierOrDeprecated(node)
        symbol(": ")
        renderType(node.detail(DocumentationNode.Kind.Type))
    }

    fun DocumentationNode.getPropertyKeyword() =
            if (details(DocumentationNode.Kind.Modifier).any { it.name == "var" }) "var" else "val"

    fun ContentBlock.identifierOrDeprecated(node: DocumentationNode) {
        if (node.deprecation != null) {
            val strike = ContentStrikethrough()
            strike.identifier(node.name)
            append(strike)
        } else {
            identifier(node.name)
        }
    }
}