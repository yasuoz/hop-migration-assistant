package jp.ac.nitech.cc.hop.assistant.constructor;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import jp.ac.nitech.cc.hop.assistant.I18n;
import org.jetbrains.annotations.NotNull;

import static jp.ac.nitech.cc.hop.assistant.constructor.HopConstructorInspection.insertAfterLastFieldOrHeader;

public class QuickFixData implements LocalQuickFix {

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
		final String	className	= psiClass.getName();
		if (className == null) {
			return;
		}
		// Java 15以降の「テキストブロック」機能を使用。
		// Kotlinの """ と .trimIndent() と全く同じ挙動がコンパイル時に実現される。
		final String constructorText = """
				public %s() {
					super();
					// TODO: Not yet implemented
				}
				""".formatted(className);
		// テキストからメソッド(コンストラクタ)のPSI要素を生成
		final PsiMethod	newConstructor	= JavaPsiFacade.getInstance(project).getElementFactory().createMethodFromText(constructorText, psiClass);

		// 先ほど作成したJavaの超高速挿入ユーティリティを叩く
		insertAfterLastFieldOrHeader(psiClass, newConstructor);
	}
}