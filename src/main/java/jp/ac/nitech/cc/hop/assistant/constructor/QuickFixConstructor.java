package jp.ac.nitech.cc.hop.assistant.constructor;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import jp.ac.nitech.cc.hop.assistant.I18n;
import org.jetbrains.annotations.NotNull;

public class QuickFixConstructor implements LocalQuickFix {
	@NotNull
	private final ConstructorBase	actor;

	QuickFixConstructor(@NotNull final ConstructorBase actor) {
		this.actor	= actor;
	}

	@NotNull
	@Override
	public String getName() {
		return I18n.message("quickfix.hop.constructor.name");
	}

	@NotNull
	@Override
	public String getFamilyName() {
		return getName();
	}

	@Override
	public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
		if (!(descriptor.getPsiElement().getParent() instanceof final PsiClass psiClass)) {
			return;
		}
		actor.createFields(psiClass);
		actor.createConstructor(psiClass);
	}
}