package jp.ac.nitech.cc.hop.assistant.constructor;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import jp.ac.nitech.cc.hop.assistant.Fixer;
import jp.ac.nitech.cc.hop.assistant.I18n;
import org.jetbrains.annotations.NotNull;

public class QuickFixExtends implements LocalQuickFix {
	/** 具象クラス */
	private final String	baseName;
	/** 1番目のGeneric */
	private final Fixer first;
	private final boolean	fixFirst;
	/** 2番目のGeneric */
	private final Fixer		second;
	private final boolean	fixSecond;

	public QuickFixExtends(
			final String	baseName,
			final Fixer 	first,
			final boolean	fixFirst,
			final Fixer		second,
			final boolean	fixSecond
	) {
		this.baseName	= baseName;
		this.first		= first;
		this.fixFirst	= fixFirst;
		this.second		= second;
		this.fixSecond	= fixSecond;
	}

	@NotNull
	@Override
	public String getName() {
		return I18n.message("quickfix.hop.extends.base");
	}

	@NotNull
	@Override
	public String getFamilyName() {
		return getName();
	}

	@Override
	public boolean startInWriteAction() {
		return true;
	}

	@Override
	public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
		final PsiElement	psiElement	= descriptor.getPsiElement();

		final PsiClass		subClass	= PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
		if (subClass == null) {
			System.err.println("[QuickFixExtends] FAILED: " + this.baseName + " subClass is null");
			return;
		}

		final PsiReferenceList	extendsList	= subClass.getExtendsList();
		if (extendsList == null) {
			System.err.println("[QuickFixExtends] FAILED: " + this.baseName + " extendsList is null");
			return;
		}

		final PsiJavaCodeReferenceElement[]	referenceElements	= extendsList.getReferenceElements();
		if (referenceElements.length > 0) {
			final var		refElement		= referenceElements[0];
			final String	baseClassText	= refElement.getReferenceName();
			if (baseClassText == null) {
				System.err.println("[QuickFixExtends] FAILED: " + this.baseName + " baseClassText is null");
				return;
			}
			// ジェネリクスの引数リストを取得
			final String	newText;
			{
				final PsiType[]		currentArgs	= refElement.getTypeParameters();
				final StringBuilder	buf			= new StringBuilder();

				buf.append(baseClassText).append('<');
				// 1番目と2番目のジェネリクス引数の置換判定
				if (this.fixFirst || currentArgs.length == 0) {
					this.first.name(buf, this.baseName);
				} else {
					buf.append(currentArgs[0].getPresentableText());
				}
				buf.append(", ");
				if (this.fixSecond || currentArgs.length < 2) {
					this.second.name(buf, this.baseName);
				} else {
					buf.append(currentArgs[1].getPresentableText());
				}
				// 新しい extends 参照テキストを組み立て
				newText	= buf.append('>').toString();
			}

			final PsiElementFactory	factory	= JavaPsiFacade.getInstance(project).getElementFactory();
			try {
				// テキストから新しい参照要素を生成し、安全に置換
				refElement.replace(factory.createReferenceFromText(newText, subClass));
			} catch (final Exception e) {
				System.err.println("[QuickFixExtends] ERROR " + this.baseName + " during PSI replacement: " + e.getMessage());
				e.printStackTrace();
			}
		} else {
			System.err.println("[QuickFixExtends] FAILED: " + this.baseName + " referenceElements is empty");
		}
	}
}