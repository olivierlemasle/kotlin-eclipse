/*******************************************************************************
* Copyright 2000-2016 JetBrains s.r.o.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
*******************************************************************************/
package org.jetbrains.kotlin.ui.editors.codeassist

import org.eclipse.core.resources.IFile
import org.eclipse.jdt.core.search.TypeNameMatch
import org.eclipse.jdt.ui.JavaElementLabels
import org.eclipse.jface.text.DocumentEvent
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension2
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension6
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.jface.viewers.StyledString
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.Point
import org.jetbrains.kotlin.builtins.isExtensionFunctionType
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.resolve.getValueParametersCountFromFunctionType
import org.jetbrains.kotlin.ui.editors.completion.KotlinCompletionUtils
import org.jetbrains.kotlin.ui.editors.quickfix.placeImports

public fun withKotlinInsertHandler(
        descriptor: DeclarationDescriptor,
        proposal: KotlinCompletionProposal,
        identifierPart: String): KotlinCompletionProposal {
    return when (descriptor) {
        is FunctionDescriptor -> {
            val parameters = descriptor.getValueParameters()
            when (parameters.size) {
                0 -> KotlinFunctionCompletionProposal(proposal, CaretPosition.AFTER_BRACKETS, false, identifierPart)

                1 -> {
                    val parameterType = parameters.single().getType()
                    if (parameterType.isFunctionType || parameterType.isExtensionFunctionType) {
                        val parameterCount = getValueParametersCountFromFunctionType(parameterType)
                        if (parameterCount <= 1) {
                            // otherwise additional item with lambda template is to be added
                            return KotlinFunctionCompletionProposal(proposal, CaretPosition.IN_BRACKETS, true, identifierPart)
                        }
                    }
                    KotlinFunctionCompletionProposal(proposal, CaretPosition.IN_BRACKETS, false, identifierPart)
                }

                else -> KotlinFunctionCompletionProposal(proposal, CaretPosition.IN_BRACKETS, false, identifierPart)
            }
        }

        else -> proposal
    }
}

fun getIdentifierInfo(document: IDocument, offset: Int): IdentifierInfo {
    val text = document.get()
    var identStartOffset = offset
    while ((identStartOffset != 0) && Character.isUnicodeIdentifierPart(text[identStartOffset - 1])) {
        identStartOffset--
    }
    return IdentifierInfo(text!!.substring(identStartOffset, offset), identStartOffset)
}

data class IdentifierInfo(val identifierPart: String, val identifierStart: Int)

open class KotlinCompletionProposal(
        val replacementString: String,
        val img: Image?,
        val presentableString: String,
        val containmentPresentableString: String? = null,
        val information: IContextInformation? = null,
        val additionalInfo: String? = null,
        identifierPart: String) : ICompletionProposal, ICompletionProposalExtension2, ICompletionProposalExtension6 {
    
    var selectedOffset = -1
    
    private @Volatile var identifierPart = identifierPart
    
    open fun getRelevance(): Int {
        return computeCaseMatchingRelevance(identifierPart.toCharArray(), replacementString.toCharArray())
    }
    
    override fun apply(viewer: ITextViewer, trigger: Char, stateMask: Int, offset: Int) {
        val document = viewer.document
        val (identifierPart, identifierStart) = getIdentifierInfo(document, offset)
        document.replace(identifierStart, offset - identifierStart, replacementString)
        
        selectedOffset = offset - identifierPart.length + replacementString.length
    }
    
    override fun validate(document: IDocument, offset: Int, event: DocumentEvent): Boolean {
        val identiferInfo = getIdentifierInfo(document, offset)
        identifierPart = identiferInfo.identifierPart
        return KotlinCompletionUtils.applicableNameFor(identiferInfo.identifierPart, replacementString)
    }
    
    override fun getSelection(document: IDocument): Point? = Point(selectedOffset, 0)

    override fun getAdditionalProposalInfo(): String? = additionalInfo

    override fun getDisplayString(): String = presentableString

    override fun getImage(): Image? = img

    override fun getContextInformation(): IContextInformation? = information
    
    override fun getStyledDisplayString(): StyledString {
        return if (containmentPresentableString != null) {
            createStyledString(getDisplayString(), containmentPresentableString)
        } else {
            StyledString(getDisplayString())
        }
    }
    
    override fun selected(viewer: ITextViewer?, smartToggle: Boolean) {
    }
    
    override fun unselected(viewer: ITextViewer?) {
    }
    
    override final fun apply(document: IDocument) {
        // should not be called
    }
}

class KotlinImportCompletionProposal(val typeName: TypeNameMatch, image: Image, val file: IFile, identifierPart: String) : 
            KotlinCompletionProposal(
                    typeName.simpleTypeName,
                    image,
                    typeName.simpleTypeName,
                    typeName.packageName,
                    identifierPart = identifierPart)  {

    var importShift = -1
    
    override fun apply(viewer: ITextViewer, trigger: Char, stateMask: Int, offset: Int) {
        super.apply(viewer, trigger, stateMask, offset)
        importShift = placeImports(listOf(typeName), file, viewer.document)
    }
    
    override fun getSelection(document: IDocument): Point? {
        val selection = super.getSelection(document)
        return if (importShift > 0 && selection != null) Point(selection.x + importShift, 0) else selection
    }
    
    override fun getRelevance(): Int {
        return -1
    }
}

private fun createStyledString(simpleName: String, containingDeclaration: String): StyledString {
    return StyledString().apply { 
        append(simpleName)
        if (containingDeclaration.isNotBlank()) {
            append(JavaElementLabels.CONCAT_STRING, StyledString.QUALIFIER_STYLER)
            append(containingDeclaration, StyledString.QUALIFIER_STYLER)
        }
    }
}