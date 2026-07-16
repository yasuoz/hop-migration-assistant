package jp.ac.nitech.cc.hop.assistant.constructor;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import jp.ac.nitech.cc.hop.assistant.I18n;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static jp.ac.nitech.cc.hop.assistant.constructor.HopConstructorInspection.hasConstructor;
import static jp.ac.nitech.cc.hop.assistant.constructor.HopConstructorInspection.insertAfterLastFieldOrHeader;

public abstract class ConstructorBase {
	final PsiElementFactory	factory;
	protected ConstructorBase(@NotNull final PsiElement element) {
		this.factory	= JavaPsiFacade.getInstance(element.getProject()).getElementFactory();
	}
	void check(final @NotNull ProblemsHolder holder, final PsiClass aClass) {
		if (hasConstructor(holder, aClass, expectedTypes())) {
			return; // すでに正しいコンストラクタがある
		}

		// コンストラクタに不備がある、または存在しない場合はエラーを登録してQuickFixを提示
		final PsiElement	problemElement	= (aClass.getNameIdentifier() != null) ? aClass.getNameIdentifier() : aClass;
		holder.registerProblem(
				problemElement,
				I18n.message("inspection.hop.constructor.problem.descriptor"),
				ProblemHighlightType.GENERIC_ERROR,
				new QuickFixConstructor(this)
		);
	}
	/** コンストラクタを作成して返す */
	protected abstract String constructor(@NotNull String className);
	/** @param	psiClass	導入先のクラス */
	protected void createConstructor(final PsiClass psiClass) {
		final String	className	= psiClass.getName();
		if (className == null) return;
		final PsiMethod	newConstructor	= factory.createMethodFromText(constructor(className), psiClass);
		insertAfterLastFieldOrHeader(psiClass, newConstructor);
	}
	/** フィールドがない場合は作成 */
	protected void createField(final PsiClass psiClass, final Expand expected, final String fieldName) {
		if (psiClass.findFieldByName(fieldName, false) == null) {
			final String	fieldText	= "private final %s\t%s;".formatted(expected.fixed, fieldName);
			final PsiField	newField	= factory.createFieldFromText(fieldText, psiClass);
			insertAfterLastFieldOrHeader(psiClass, newField);
		}
	}
	/** 必要なフィールドを構築 */
	protected void createFields(final PsiClass psiClass) {}
	protected abstract List<Expand> expectedTypes();
}