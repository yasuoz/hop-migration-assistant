package jp.ac.nitech.cc.hop.assistant.edit;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;

public class EditList {
	private final PsiJavaFile			javaFile;
	public final String					origText;
	private final ArrayList<EditText>	reserves	= new ArrayList<>();

	public EditList(@NotNull final PsiJavaFile javaFile) {
		this.javaFile	= javaFile;
		origText		= javaFile.getText();
	}
	public EditText.Insert insert(final int index, final String ... phrase) {
		if (phrase == null || phrase.length == 0) return null;
		final EditText.Insert	target;
		SEARCH: {
			for (final EditText prev : reserves) {
				if (prev.oldStart == index && prev instanceof EditText.Insert prevInsert) {
					target	= prevInsert;
					break SEARCH;
				}
			}
			target	= new EditText.Insert(index);
			reserves.add(target);
		}
		for (final String str : phrase) {
			target.add(str);
		}
		return target;
	}
	/** elementFactory.createReferenceFromText({@param replace}, null)に類似 */
	public EditText.Replace replace(@NotNull final PsiElement element, @NotNull final String replace) {
		return replace(element, replace, -1, -1);
	}
	public EditText.Replace replace(@NotNull final PsiElement element, @NotNull final String replace, final int newStart, final int newEnd) {
		final var	range	= element.getTextRange();
		return replace(range.getStartOffset(), range.getEndOffset(), replace, newStart, newEnd);
	}
	public EditText.Replace replace(final int oldStart, final int oldEnd, @NotNull final String replace) {
		return replace(oldStart, oldEnd, replace, -1, -1);
	}
	public EditText.Replace replace(final int oldStart, final int oldEnd, @NotNull final String replace, final int newStart, final int newEnd) {
		final var	edit	=  new EditText.Replace(oldStart, oldEnd, replace, newStart, newEnd);
		SEARCH: {
			final int	max		= reserves.size();
			for(int i = 0; i < max; i++) {
				final var	current	= reserves.get(i);
				if (current.oldStart >= oldStart && current.oldEnd <= oldEnd) {
					reserves.set(i, edit);
					break SEARCH;
				}
			}
			reserves.add(edit);
		}
		return edit;
	}
	public boolean commit() {
		if (reserves.isEmpty()) return true;
		Collections.sort(reserves);
		int	length	= origText.length();
		for (final EditText edit : reserves) {
			length	+= edit.increment();
		}
		final StringBuilder	buf	= new StringBuilder(length);
		int	prev	= 0;
		for (final EditText edit : reserves) {
			if (prev < edit.oldStart) {
				buf.append(origText, prev, edit.oldStart);
			}
			edit.append(buf);
			prev	= edit.oldEnd;
		}
		buf.append(origText, prev, origText.length());
		return new ReplaceAll(javaFile, buf.toString()).commit();
	}


	@Deprecated
	private static class ReplaceAll implements ThrowableRunnable<IncorrectOperationException> {
		private final @NotNull Project	project;
		private final Document			document;
		private final PsiJavaFile		javaFile;
		private final String			newText;
		private ReplaceAll(final PsiJavaFile javaFile, final String newText) {
			this.project	= javaFile.getProject();
			this.javaFile	= javaFile;
			document		= PsiDocumentManager.getInstance(project).getDocument(javaFile);
			this.newText	= newText;
		}

		private boolean commit() throws IncorrectOperationException {
			if (document == null) return false;
			WriteCommandAction.writeCommandAction(project, javaFile)
					.withName("Replace Multiple Imports").run(this);
			return true;
		}
		@Override
		public void run() throws IncorrectOperationException {
			document.setText(newText);
		}
	}
}