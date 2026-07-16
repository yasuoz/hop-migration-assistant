package jp.ac.nitech.cc.hop.assistant.constructor;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import jp.ac.nitech.cc.hop.assistant.Fixer;
import jp.ac.nitech.cc.hop.assistant.I18n;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.psi.util.InheritanceUtil.isInheritor;
import static jp.ac.nitech.cc.hop.assistant.imports.HopImportInspection.emptyHopLibrary;

public class HopConstructorInspection extends AbstractBaseJavaLocalInspectionTool {
	public static final String	DATA			= "Data"
		,						DIALOG			= "Dialog"
		,						I_VARIABLES		= "org.apache.hop.core.variables.IVariables"
		,						META			= "Meta"
		,						PIPELINE		= "org.apache.hop.pipeline.Pipeline"
		,						PIPELINE_META	= "org.apache.hop.pipeline.PipelineMeta"
		,						PUBLIC			= "public"
		,						SHELL			= "org.eclipse.swt.widgets.Shell"
		,						TRANS_META		= "org.apache.hop.pipeline.transform.TransformMeta"
		,						WORKFLOW_META	= "org.apache.hop.workflow.WorkflowMeta";

	static final Expand			EX_INT			= new Expand(int.class.getCanonicalName(), null)
			,					EX_PIPELINE		= new Expand(PIPELINE, null)
			,					EX_PIPELINE_META= new Expand(PIPELINE_META, null)
			,					EX_SHELL		= new Expand(SHELL, null)
			,					EX_TRANS_META	= new Expand(TRANS_META, null)
			,					EX_VARIABLES	= new Expand(I_VARIABLES, null)
			,					EX_WORKFLOW		= new Expand(WORKFLOW_META, null);
	/**
	 * 1番目と2番目のジェネリックを確認します。
	 * プリミティブチェックを優先し、無駄な配列アクセスやNullバグを鉄壁に防ぐJava実装です。
	 */
	private static void checkGenerics(
			@NotNull final ProblemsHolder	holder,
			@NotNull final PsiClass			aClass,
			@NotNull final String			className,
			@NotNull final Fixer			base,
			@NotNull final Fixer			first,
			@NotNull final Fixer			second
	) {
		if (!isInheritor(aClass, base.baseClass)) {
			return;
		}
		// extendsList が null の場合は即座に早期リターン
		final var extendsList = aClass.getExtendsList();
		if (extendsList == null) {
			return;
		}
		final PsiJavaCodeReferenceElement[] extendsElements = extendsList.getReferenceElements();

		// 継承されたジェネリックの解析も含めて Base クラス（BaseTransformMetaなど）のクラス情報を取得
		final PsiClass baseMetaClass = JavaPsiFacade.getInstance(aClass.getProject())
				.findClass(base.baseClass, aClass.getResolveScope());
		if (baseMetaClass == null) {
			return;
		}

		final PsiTypeParameter[]	typeParameters	= baseMetaClass.getTypeParameters();
		final boolean	fixFirst;
		final boolean	fixSecond;

		if (typeParameters.length > 0) {
			// ジェネリックの型置換(Substitutor)を取得して具象型を割り出す
			final PsiSubstitutor	substitutor	= TypeConversionUtil.getSuperClassSubstitutor(
					baseMetaClass, aClass, PsiSubstitutor.EMPTY
			);

			final PsiType firstType		= substitutor.substitute(typeParameters[0]);
			// 長さが2以上の場合のみ2番目の型を取得
			final PsiType secondType	= (typeParameters.length > 1) ? substitutor.substitute(typeParameters[1]) : null;

			// checkHopTypeの戻り値が2であるかどうかで置換の必要性を判定
			fixFirst	= 2 == checkHopType(firstType, first.interfaceClass);
			fixSecond	= 2 == checkHopType(secondType, second.interfaceClass);
		} else {
			fixFirst	= true;
			fixSecond	= true;
		}

		// どちらか一方でも修正が必要な場合、警告を登録してQuickFixを提示
		if (fixFirst || fixSecond) {
			// 警告を引くターゲット要素の選定（extendsがあればその最初の要素、無ければクラス名部分）
			final PsiElement problemElement = (extendsElements.length > 0)
					? extendsElements[0]
					: (aClass.getNameIdentifier() != null ? aClass.getNameIdentifier() : aClass);

			final String	rawClassName	= aClass.getName();
			final String	baseName		= base.base(rawClassName != null ? rawClassName : className);

			holder.registerProblem(
					problemElement,
					"BaseTransformMeta requires concrete Transform and Data classes in deep generics hierarchy.",
					ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
					new QuickFixExtends(baseName, first, fixFirst, second, fixSecond)
			);
		}
	}
	/**
	 * ### {@param type}のジェネリックが{@param expected}を継承しているかを確認
	 * |0|完全合格!|
	 * |-|-|
	 * |1|型変数のまま(意図された中間の親クラスなので触らない)|
	 * |2|文字列はあるがクラスが見当たらない、またはインターフェース継ぎ忘れ|
	 */
	private static int checkHopType(final PsiType type, final String expected) {
		// そもそも型が取れない場合は直せる
		if (type == null) {
			return 2;
		}
		// PsiTypeから実際のクラス定義(PsiClass)の解決を試みる
		if (type instanceof final PsiClassType classType) {
			final PsiClass	targetClass	= classType.resolve();

			// 【ケース1：クラスが見当たらない(打ち間違い・未作成)】
			// 文字列としては `<Some>` のように書かれているが、実体クラスが見つからない(コンパイルエラー)
			if (targetClass == null) {
				return 2;
			}
			// 【ケース2：ただの型変数のまま(中間の親クラス)】
			// クラスの実体はあるけれど、それが「型変数(AやBの文字のまま)」である場合は、
			// 触ってはいけない中間のファイルなので1を返します。
			if (targetClass instanceof PsiTypeParameter) {
				return 1;
			}
			// 【ケース3：クラスはあるけれど、インターフェースを継ぎ忘れている】
			// クラスは実在するのに、ITransform などを引いていない場合は、同じく直しやすい2を返します。
			final boolean isInheritor = isInheritor(targetClass, expected);
			return isInheritor ? 0 : 2;
		}
		// PsiClassType 以外(プリミティブ型など、通常あり得ないケース)は2を返して安全に倒す
		return 2;
	}
	/**
	 * ### {@param aClass}のクラスに{@param expectedTypes}の引数を持つpublicなコンストラクタが存在するかを確認
	 * 引数は合っているが、コンストラクタがpublicでない場合は、{@param holder}を介して解決策を提案<br/>
	 * falseが返った場合はコンストラクタを作成するクイックフィックスを提示してください
	 */
	static boolean hasConstructor(
			@NotNull final ProblemsHolder	holder,
			@NotNull final PsiClass			aClass,
			@NotNull final List<Expand>		expectedTypes
	) {
		final PsiMethod[]	constructors	= aClass.getConstructors();
		final int			expectedSize	= expectedTypes.size();

		// 伝統的なネストループの制御。Javaではフラグ変数を用いることで、
		// 隠れたオブジェクト生成（Kotlinのインデックス範囲オブジェクト等）を完全に排除し、最速で回せます。
		CONSTRUCTOR: for (final PsiMethod constructor : constructors) {
			final PsiParameter[] parameters = constructor.getParameterList().getParameters();

			if (parameters.length != expectedSize) continue;

			// 引数の型チェック
			for (int i = 0; i < expectedSize; i++) {
				final String	currentParamType	= parameters[i].getType().getCanonicalText();
				final Expand	expectedType		= expectedTypes.get(i);
				if (!currentParamType.equals(expectedType.fixed)) {
					continue CONSTRUCTOR;	// このコンストラクタは不適合。内側を抜けて次のコンストラクタへ
				}
			}

			// すべての引数が一致した場合の処理
			if (!constructor.hasModifierProperty(PUBLIC)) {
				// ただし、publicではなかった場合はpublicにする警告を登録
				final PsiElement problemElement = (constructor.getNameIdentifier() != null)
						? constructor.getNameIdentifier() : aClass;

				holder.registerProblem(
						problemElement,
						I18n.message("inspection.hop.constructor.problem.public"),
						ProblemHighlightType.GENERIC_ERROR,
						new QuickFixPublish(constructor.getModifierList())
				);
			}
			return true;
		}

		// 引数なしコンストラクタを期待しており、かつコンストラクタが1つもない(デフォルトコンストラクタ動作の)場合
		if (constructors.length == 0 && expectedSize == 0) {
			if (!aClass.hasModifierProperty(PUBLIC)) {
				final PsiModifierList classMList = aClass.getModifierList();
				if (classMList != null) {
					final PsiElement problemElement = (aClass.getNameIdentifier() != null)
							? aClass.getNameIdentifier() : aClass;

					holder.registerProblem(
							problemElement,
							I18n.message("inspection.hop.constructor.problem.public"),
							ProblemHighlightType.GENERIC_ERROR,
							new QuickFixPublish(classMList)
					);
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * ### {@param psiClass}クラスのフィールド定義の後ろに{@param newElement}を挿入
	 */
	public static void insertAfterLastFieldOrHeader(
			@NotNull final PsiClass psiClass,
			@NotNull final PsiElement newElement
	) {
		final PsiField[] fields = psiClass.getFields();
		final com.intellij.psi.codeStyle.JavaCodeStyleManager styleManager =
				com.intellij.psi.codeStyle.JavaCodeStyleManager.getInstance(psiClass.getProject());

		final PsiElement insertedElement;
		if (fields.length > 0) {
			// クラス内に書かれている「一番最後のフィールド(変数定義)」を取得
			// Kotlinの fields.last()を、配列のインデックス引き算(length - 1)で置き換え
			final PsiField lastField = fields[fields.length - 1];

			// その最後の変数の「直後」を狙って、新しく作ったコンストラクター(または変数)を挿入
			insertedElement = psiClass.addAfter(newElement, lastField);
		} else {
			// もしクラス内に変数が1つも無ければ、クラスの開始波括弧「 { 」の直後に配置
			final PsiElement leftBrace = psiClass.getLBrace();
			if (leftBrace != null) {
				insertedElement = psiClass.addAfter(newElement, leftBrace);
			} else {
				insertedElement = psiClass.add(newElement); // 最終フォールバック
			}
		}

		// 挿入されたコードに対して、HopのFQN（org.apache...）を自動で ShortName に縮め、インポートを整理させる
		if (insertedElement != null) {
			styleManager.shortenClassReferences(insertedElement);
		}
	}
	/**
	 * ### javaコードを検査
	 * メモリ参照を最優先し、無駄なオブジェクト生成を徹底的に排除した最速のビジター実装です。
	 */
	@NotNull
	@Override
	public PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
		if (emptyHopLibrary(holder)) {
			return PsiElementVisitor.EMPTY_VISITOR;
		}
		return new JavaElementVisitor() {
			@Override
			public void visitClass(@NotNull final PsiClass aClass) {
				super.visitClass(aClass);

				// 【最速】抽象クラス、インターフェース、インナークラスは即座に対象外にしてリターン
				if (aClass.isInterface() || aClass.hasModifierProperty("abstract") || aClass.getContainingClass() != null) {
					return;
				}

				final String	className	= aClass.getQualifiedName();
				// 【最速】パッケージ名がない(デフォルトパッケージの)クラス名は除く
				if (className == null || className.indexOf('.') == -1) {
					return;
				}

				// --- 【Dialog】 ---
				if (className.endsWith(DIALOG)) {
					final ConstructorBase	actor;
					// Action系なら末尾のDialogを取る、Transform系なら末尾をMetaに変える
					if (isInheritor(aClass, Fixer.ACTION_DIALOG.interfaceClass)) {
						actor	= new ConstructorActionDialog(aClass,
								className.substring(0, className.length() - DIALOG.length()));
					} else if(isInheritor(aClass, Fixer.TRANS_DIALOG.interfaceClass)) {
						actor	= new ConstructorTransDialog(aClass,
								new StringBuilder().append(className, 0, className.length() - DIALOG.length())
										.append(META).toString());
					} else return;
					actor.check(holder, aClass);
				} else if (className.endsWith(DATA)) {
					// --- 【Data】 ---
					if (isInheritor(aClass, Fixer.TRANS_DATA.baseClass)
							&& !hasConstructor(holder, aClass, List.of())) {

						final PsiElement problemElement = (aClass.getNameIdentifier() != null) ? aClass.getNameIdentifier() : aClass;
						holder.registerProblem(
								problemElement,
								I18n.message("inspection.hop.constructor.problem.descriptor"),
								ProblemHighlightType.GENERIC_ERROR,
								new QuickFixData()
						);
					}
				} else if (className.endsWith(META)) {
					// --- 【Meta】SomeMeta<Some, SomeData> ---
					checkGenerics(holder, aClass, className, Fixer.TRANS_META, Fixer.TRANS, Fixer.TRANS_DATA);
				} else if(isInheritor(aClass, Fixer.TRANS.interfaceClass)) {
					// --- 【Main】Some<SomeMeta, SomeData> ---
					checkGenerics(holder, aClass, className, Fixer.TRANS, Fixer.TRANS_META, Fixer.TRANS_DATA);
					new ConstructorTrans(aClass, className).check(holder, aClass);
				}
			}
		};
	}
}