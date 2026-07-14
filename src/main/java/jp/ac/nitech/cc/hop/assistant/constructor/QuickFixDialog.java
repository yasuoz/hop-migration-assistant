package jp.ac.nitech.cc.hop.assistant.constructor;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import jp.ac.nitech.cc.hop.assistant.I18n;
import org.jetbrains.annotations.NotNull;

import static jp.ac.nitech.cc.hop.assistant.constructor.HopConstructorInspection.*;

public class QuickFixDialog implements LocalQuickFix {
	/** 具象クラス */
	private final String expectedName;
	/** Action系かTransform系かを判定するフラグ */
	private final boolean isAction;

	public QuickFixDialog(final String expectedName, final boolean isAction) {
		this.expectedName = expectedName;
		this.isAction = isAction;
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
		// Kotlinの安全キャストを Java の Pattern Matching でスマートかつフラットに紐解く
		if (!(descriptor.getPsiElement().getParent() instanceof final PsiClass psiClass)) {
			return;
		}

		final var factory = JavaPsiFacade.getInstance(project).getElementFactory();
		final String fieldName = this.isAction ? "action" : "meta";

		// フィールド（メンバ変数）がまだ定義されていなければ、自動で挿入する
		if (psiClass.findFieldByName(fieldName, false) == null) {
			final String fieldText = "private final %s\t%s;".formatted(this.expectedName, fieldName);
			final PsiField newField = factory.createFieldFromText(fieldText, psiClass);
			insertAfterLastFieldOrHeader(psiClass, newField);
		}

		// 系統に応じて、正しいシグネチャのコンストラクター構造を生成
		if (this.isAction) {
			generateActionStructure(psiClass, factory, fieldName);
		} else {
			generateTransformStructure(psiClass, factory, fieldName);
		}
	}

	/**
	 * Action用の構造生成 (Shell, 具象Action, WorkflowMeta, IVariables)
	 */
	private void generateActionStructure(final PsiClass psiClass, final PsiElementFactory factory, final String fieldName) {
		final String className = psiClass.getName();
		if (className == null) return;

		// 閉じクォートを左端に揃えることで、プレフィックスのタブやインデントを100%安全に維持
		final String constructorText = """
				public %s(
					final %s\t\tparent, 
					final %s\taction, 
					final %s\t\tworkflowMeta, 
					final %s\tvariables
				) {
					super(parent, workflowMeta, variables);
					this.%s\t= action;
					// TODO: Not yet implemented
				}
				""".formatted(className, SHELL, this.expectedName, WORKFLOW, I_VARIABLES, fieldName);

		final PsiMethod newConstructor = factory.createMethodFromText(constructorText, psiClass);
		insertAfterLastFieldOrHeader(psiClass, newConstructor);
	}

	/**
	 * Transform用の構造生成 (Shell, IVariables, 具象Meta, PipelineMeta)
	 */
	private void generateTransformStructure(final PsiClass psiClass, final PsiElementFactory factory, final String fieldName) {
		final String className = psiClass.getName();
		if (className == null) return;

		final String constructorText = """
				public %s(
					final %s\t\tparent, 
					final %s\tvariables, 
					final %s\ttransformMeta, 
					final %s\t\tpipelineMeta
				) {
					super(parent, variables, transformMeta, pipelineMeta);
					this.%s\t= transformMeta;
					// TODO: Not yet implemented
				}
				""".formatted(className, SHELL, I_VARIABLES, this.expectedName, PIPELINE, fieldName);

		final PsiMethod newConstructor = factory.createMethodFromText(constructorText, psiClass);
		insertAfterLastFieldOrHeader(psiClass, newConstructor);
	}
}