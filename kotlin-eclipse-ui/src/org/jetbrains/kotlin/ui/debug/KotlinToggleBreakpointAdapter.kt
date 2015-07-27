/*******************************************************************************
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.kotlin.ui.debug

import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.CoreException
import org.eclipse.debug.ui.actions.IToggleBreakpointsTarget
import org.eclipse.jdt.debug.core.IJavaLineBreakpoint
import org.eclipse.jdt.debug.core.JDIDebugModel
import org.eclipse.jface.text.BadLocationException
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jface.viewers.ISelection
import org.eclipse.ui.IWorkbenchPart
import org.eclipse.ui.texteditor.ITextEditor
import org.jetbrains.kotlin.core.builder.KotlinPsiManager
import org.jetbrains.kotlin.core.log.KotlinLogger
import org.jetbrains.kotlin.eclipse.ui.utils.EditorUtil
import org.jetbrains.kotlin.load.kotlin.PackageClassUtils
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.JetClass
import org.jetbrains.kotlin.psi.JetFile
import org.jetbrains.kotlin.psi.JetPsiUtil
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.core.utils.getDeclaringTypeFqName
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.JetElement

public class KotlinToggleBreakpointAdapter : IToggleBreakpointsTarget {
    override public fun toggleLineBreakpoints(part: IWorkbenchPart, selection: ISelection) {
        val editor = getEditor(part)
        if (editor == null) return
        
        val file = EditorUtil.getFile(editor)
        if (file == null) {
            KotlinLogger.logError("Failed to retrieve IFile from editor " + editor, null)
            return
        }
        
        val lineNumber = (selection as ITextSelection).getStartLine() + 1
        val document = editor.getDocumentProvider().getDocument(editor.getEditorInput())
        val typeName = getTypeNameFqName(document, lineNumber, file)
        if (typeName == null) return
        
        val existingBreakpoint = JDIDebugModel.lineBreakpointExists(file, typeName, lineNumber)
        if (existingBreakpoint != null) {
            existingBreakpoint.delete()
        } else {
            JDIDebugModel.createLineBreakpoint(file, typeName, lineNumber, -1, -1, 0, true, null)
        }
    }
    
    override public fun canToggleLineBreakpoints(part: IWorkbenchPart, selection: ISelection): Boolean = true
    
    override public fun toggleMethodBreakpoints(part: IWorkbenchPart, selection: ISelection) {}
    
    override public fun canToggleMethodBreakpoints(part: IWorkbenchPart, selection: ISelection): Boolean = true
    
    override public fun toggleWatchpoints(part: IWorkbenchPart, selection: ISelection) {}
    
    override public fun canToggleWatchpoints(part: IWorkbenchPart, selection: ISelection): Boolean = true
    
    private fun getTypeNameFqName(document: IDocument, lineNumber: Int, file: IFile): String? {
        val kotlinParsedFile = KotlinPsiManager.INSTANCE.getParsedFile(file)
        val typeName = findTopmostTypeFqName(document.getLineOffset(lineNumber - 1), kotlinParsedFile)
        
        return typeName?.asString()
    }
    
    private fun findTopmostTypeFqName(offset: Int, jetFile: JetFile): FqName? {
        val element = jetFile.findElementAt(offset)
        val jetElement = PsiTreeUtil.getNonStrictParentOfType(element, javaClass<JetElement>())
        
        return if (jetElement != null) getDeclaringTypeFqName(jetElement) else null
    }
    
    private fun getEditor(part: IWorkbenchPart): ITextEditor? {
        return if (part is ITextEditor) part else part.getAdapter(javaClass<ITextEditor>()) as? ITextEditor
    }
}