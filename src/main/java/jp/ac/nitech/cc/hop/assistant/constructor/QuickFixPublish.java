package jp.ac.nitech.cc.hop.assistant.constructor;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiModifierList;
import jp.ac.nitech.cc.hop.assistant.I18n;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.PsiModifier.*;

public class QuickFixPublish implements LocalQuickFix {
	private final PsiModifierList	modifierList;

	public QuickFixPublish(final PsiModifierList modifierList) {
		this.modifierList	= modifierList;
	}

	@NotNull
	@Override
	public String getName() {
		return I18n.message("inspection.hop.constructor.problem.public");
	}

	@NotNull
	@Override
	public String getFamilyName() {
		return "HopConstructorVerifier";
	}

	@Override
	public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
		// もし修飾子リストが何らかの理由でツリーから消失していた場合のセーフティガード
		if (modifierList == null) {
			return;
		}
		modifierList.setModifierProperty(PRIVATE,	false);
		modifierList.setModifierProperty(PROTECTED,	false);
		modifierList.setModifierProperty(PUBLIC,	true);
	}
}