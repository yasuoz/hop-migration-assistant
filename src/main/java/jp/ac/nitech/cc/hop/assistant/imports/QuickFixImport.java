package jp.ac.nitech.cc.hop.assistant.imports;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import jp.ac.nitech.cc.hop.assistant.I18n;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.text.StringUtilRt.isEmpty;
import static jp.ac.nitech.cc.hop.assistant.constructor.HopConstructorInspection.*;
import static jp.ac.nitech.cc.hop.assistant.imports.Converter.EMPTY_STRING;

/**
 * ファイル内のすべてのPentahoクラスをHopに置き換える
 */
public class QuickFixImport extends LocalQuickFixAndIntentionActionOnPsiElement {
	private static final com.intellij.openapi.diagnostic.Logger LOGGER = com.intellij.openapi.diagnostic.Logger.getInstance(QuickFixImport.class);

	private static final Pattern	PAT_CONSTS		= Pattern.compile("(?:^|\\bConst\\.)(?:FILE_EXTENSION|INTERNAL_VARIABLE|STRING)_(JOB|TRANSF(?:ORMATION)?)(?=(?:_[A-Z]+)+$)")
			,						PAT_LOAD_XML	= Pattern.compile("^loadX[Mm][Ll]\\w{0,5}$")
			,						PAT_META_METHOD	= Pattern.compile("^((?:create|get|is|reset|se(?:archInfoAndTarget|t))\\w{0,5})(?:Step|Trans)(?=(?:Data|(?:I[Oo])?Meta(?:InjectionInterface)?)?\\w?$)")
			,						PAT_VARIABLES	= Pattern.compile("\\b(?:trans|job|step)(Meta\\b)");
	public static final String		DUMMY_JAVA		= "Dummy.java";
	private static final String		CATEGORY_ATTR	= "categoryDescription"
			,						I18N_PREFIX		= "i18n:"
			,						INDENT_TEXT		= "\n\t\t"
			,						META_ANNOTATION	= "@HopMetadataProperty"
			,						META_PROPERTY	= "org.apache.hop.metadata.api.HopMetadataProperty"
			,						PIPELINE		= "Pipeline"
			,						TRANSFORM		= "Transform";

	QuickFixImport(@Nullable PsiElement element) {
		super(element);
	}

	@NotNull
	@Override
	public String getText() {
		return I18n.message("quickfix.hop.import.migration.name");
	}

	@NotNull
	@Override
	public String getFamilyName() {
		return "PentahoToHopImportMigration";
	}

	@Override
	public void invoke(
			final @NotNull Project		project,
			final @NotNull PsiFile		file,
			final @Nullable Editor		editor,
			final @NotNull PsiElement	startElement,
			final @NotNull PsiElement	endElement
	) {
		if (!(file instanceof final PsiJavaFile original)) return;
		final PsiJavaFile	javaFile	= (PsiJavaFile) PsiFileFactory.getInstance(project)
				.createFileFromText(
						original.getName(),
						JavaLanguage.INSTANCE,
						original.getText(),
						false, // イベントを発生させず完全に隔離する
						false
				);
		final var	importList	= javaFile.getImportList();
		if (importList == null) return;

		final PsiImportStatementBase[]			allImports		= importList.getAllImportStatements();
		final List<PsiJavaCodeReferenceElement> references;
		final ReplaceAll						replacer		= new ReplaceAll(project, original, javaFile);
		final PsiElementFactory					elementFactory	= JavaPsiFacade.getInstance(project).getElementFactory();
		final PsiFileFactory					fileFactory		= PsiFileFactory.getInstance(project);
		{
			// ファイル内のすべてのコード参照(型宣言、new、キャスト等)を全取得
			final var	javaList		= PsiTreeUtil.findChildrenOfType(javaFile, PsiJavaCodeReferenceElement.class);
			final var	constMatcher	= PAT_CONSTS.matcher(INDENT_TEXT);
			final var	replaceList		= new ArrayList<ReplaceSet>();
			references	= new LinkedList<>();
			for (final PsiJavaCodeReferenceElement ref : javaList) {
				if (PsiTreeUtil.getParentOfType(ref, PsiImportStatementBase.class) != null) continue;
				final String	fqn1	= ref.getText();
				if (constMatcher.reset(fqn1).find()) {
					if (ref.resolve() != null) continue;
					final boolean		isTrans;
					final StringBuilder	buf		= new StringBuilder(fqn1.length() + 20);
					{
						final int	start	= constMatcher.start(1);
						isTrans				= fqn1.charAt(start) == 'T';
						buf.append(fqn1, 0, start).append(isTrans ? "PIPELINE" : "WORKFLOW");
					}
					final int	from	= constMatcher.end(1);
					replaceList.add(new ReplaceSet(ref,
							elementFactory.createExpressionFromText(buf.append(fqn1, from, fqn1.length()).toString(), ref.getParent())));
					continue;
				} else if (ref.resolve() != null) continue;
				if (ref.getQualifier() != null) {
					if (ref.getParent() instanceof PsiJavaCodeReferenceElement ||
							!fqn1.startsWith("org.pentaho")) continue;
					// -----------------------------------------------------------------
					// ここに到達したrefは、メソッドチェーンも含めた「一番右端」
					// 例: "org.pentaho.di...RowMetaInterface.static_method().getParentStepMeta"
					// -----------------------------------------------------------------
					// 右から左(子・クオリファイア方向)へ芋づる式に遡る
					//LOGGER.info("==右端: " + ref.getText() + "	- " + ref.getClass().getCanonicalName());
					for (PsiJavaCodeReferenceElement current = ref;;) {
						final String	fqn2	= current.getText();
						if (fqn2.indexOf('(') < 0 && isUpper(current.getReferenceName())) {
							final var convert = Converter.from(fqn2);
							if (!convert.stay) {
								final PsiFile	dummyFile = fileFactory
										.createFileFromText(DUMMY_JAVA, JavaLanguage.INSTANCE, convert.toString(), false, false);
								replacer.replace(current, PsiTreeUtil.findChildOfType(dummyFile, PsiJavaCodeReferenceElement.class));
							}
							break;
						}
						// さらに左(子)へ移動
						if (current.getQualifier() instanceof PsiJavaCodeReferenceElement childRef) {
							current	= childRef;
						} else {
							break;
						}
					}
					continue;
				}
				// 最左端: (自分の左側にパッケージなどのクオリファイアが一切ない)要素
				// クラスが解決するものを除外
				if (!"Const".equals(ref.getReferenceName()) && ref.resolve() != null) continue;
				references.add(ref);
			}
			for (final ReplaceSet set : replaceList) {
				set.from.replace(set.to);
			}
			if (allImports.length == 0 && references.isEmpty()) return;
		}

		final var	parserFacade	= PsiParserFacade.getInstance(project);
		// PSI要素から「生テキスト」を引っこ抜き、Converterに丸投げして自律判定させる
		for (final PsiImportStatementBase imp : allImports) {
			// エディタ上に実際に書かれている「import static ... ;」の生テキストを最速取得
			final String	rawImport	= imp.getText();
			final var		converter	= Converter.from(rawImport);
			// 変換対象外
			if (converter.stay) continue;
			final String	newFqn		= converter.toString();
			if (converter.renamed) {
				// 古いShortNameと新しいShortNameを特定する
				final String	oldShortName	= converter.getOldName();
				final String	newShortName	= converter.getNewName();
				// 本文中のShortNameをセットで置き換え
				final Iterator<PsiJavaCodeReferenceElement> it	= references.iterator();
				while (it.hasNext()) {
					final PsiJavaCodeReferenceElement	ref	= it.next();
					if (ref.getText().equals(oldShortName)) {
						replacer.replace(ref, elementFactory.createReferenceFromText(newShortName, null));
						it.remove();
					}
				}
			}
			{	// クラスが実在していなくても、import構文チェックだけで置き換えるための方法
				final var	dummyFile	= (PsiJavaFile) fileFactory.createFileFromText(DUMMY_JAVA, JavaLanguage.INSTANCE, newFqn);
				final var	imports		= dummyFile.getImportList();
				if (imports != null) {
					replacer.replace(imp, imports.getAllImportStatements()[0]);
				}
			}
		}
		// =================================================================
		// Hop化されたアノテーション(Transform / Action)の形式を整える
		// =================================================================
		for (final PsiAnnotation annotation : PsiTreeUtil.findChildrenOfType(javaFile, PsiAnnotation.class)) {
			if (!(annotation.getParent() instanceof final PsiModifierList owner) || !(owner.getParent() instanceof PsiClass)) {
				continue;
			}
			final String	annoName	= annotation.getQualifiedName();
			if (!(	"Action".equals(annoName) ||	TRANSFORM.equals(annoName) ||
					"org.apache.hop.core.annotations.Action".equals(annoName) ||
					"org.apache.hop.core.annotations.Transform".equals(annoName))) {
				// 対象外のアノテーションはスルー
				continue;
			}
			// categoryDescription属性を取得
			final var	categoryAttr	= annotation.findDeclaredAttributeValue(CATEGORY_ATTR);

			// 属性が存在し、かつダブルクォーテーションで囲まれた文字列リテラルである場合
			if (categoryAttr instanceof final PsiLiteralExpression literal) {
				final String	oldCategoryValue	= (String) literal.getValue(); // クォーテーションなしの生文字列

				if (oldCategoryValue != null && oldCategoryValue.startsWith(I18N_PREFIX)) {
					final StringBuilder	buf	= new StringBuilder().append('"').append(I18N_PREFIX);
					// 新しい文字列リテラル要素（例: "i18n:org.apache.hop..."）に変換
					final var	convert	= Converter.from(buf, oldCategoryValue, I18N_PREFIX.length(), oldCategoryValue.length());
					if (convert.stay) break;
					// クォーテーションを戻すためにダブルクォーテーションで挟む
					buf.append('"');
					final PsiAnnotationMemberValue	newLiteral	= elementFactory.createExpressionFromText(convert.toString(), null);
					annotation.setDeclaredAttributeValue(CATEGORY_ATTR, newLiteral);
				}
			}
			break;
		}
		final var methodCalls = PsiTreeUtil.findChildrenOfType(javaFile, PsiMethodCallExpression.class);
		// =================================================================
		// 特殊なメソッド呼び出しのピンポイント置換
		// =================================================================
		{
			final Matcher	matcher	= PAT_META_METHOD.matcher(EMPTY_STRING);
			for (final PsiMethodCallExpression call : methodCalls) {
				final var	methodExpr	= call.getMethodExpression();
				if (methodExpr.resolve() != null) continue;
				// メソッド名が記述されている「識別子(Identifier)ノード」を直接取得
				final PsiElement	nameElement	= methodExpr.getReferenceNameElement();
				if (nameElement == null) continue;
				final String		oldName, newName;

				// メソッド名だけのマッピング判定
				oldName	= nameElement.getText();
				switch (oldName) {
					case "findStep"
							-> newName	= "findTransform";
					case "getDestinationStepName"
							-> newName	= "getDestinationTransformName";
					case "getPartitionID"
							-> newName	= "getPartitionId";
					case "getTrans"	// IPipelineEngine<PipelineMeta> tr = getPipeline();
							-> newName	= "getPipeline";
					case "shareVariablesWith"
							-> newName	= "shareWith";
					case null -> {
						continue;
					}
					default -> {
						newName	= renameMethod(matcher, oldName);
						if (newName == null) continue;
					}
				}
				nameElement.replace(elementFactory.createIdentifier(newName));
			}
		}
		// =================================================================
		// 典型的な変換漏れのクラス名、Constクラスの内部レガシー定数
		// =================================================================
		{
			final Matcher	varMatcher		= PAT_VARIABLES.matcher(INDENT_TEXT);
			for (final PsiJavaCodeReferenceElement ref : references) {
				final String	oldName = ref.getText()
						,		newName;
				{
					varMatcher.reset(oldName);
					if (varMatcher.find()) {
						final char			type		= oldName.charAt(varMatcher.start());
						final PsiExpression	baseExpr	= PsiTreeUtil.getParentOfType(ref, PsiExpression.class);
						if (baseExpr != null) {
							PsiExpression	targetExpr	= baseExpr;
							while (targetExpr.getParent() instanceof final PsiExpression parent) {
								targetExpr	= parent;
							}
							// 例: "transMeta.isUsingUniqueConnections()" -> "getPipelineMeta().isUsingUniqueConnections()"
							final StringBuilder	buf;
							ERROR: {	// 廃止メソッドを使ってそうな時だけコメントアウト
								final String	test	= targetExpr.getText();
								final String	errMsg, result;
								buf	= new StringBuilder(test.length() + 100);
								if (test.contains(".isUsingUniqueConnections()")) {
									errMsg	= I18n.message("quickfix.hop.removed.isUsingUniqueConnections");
									result	= "false";
								} else break ERROR;
								if (varMatcher.reset(test).find()) {
									replaceVarMeta(buf.append("var a=/* FIXME	"), varMatcher, test, type);
									buf.append('	').append(errMsg).append(" */ ").append(result).append(";");
								}
								final PsiFile	dummyFile	= fileFactory.createFileFromText(
										DUMMY_JAVA,
										JavaLanguage.INSTANCE,
										buf.toString(),
										false,
										false
								);
								// PsiFieldとして作成されている。
								final PsiVariable	variableNode	= PsiTreeUtil.findChildOfType(dummyFile, PsiVariable.class);
								if (variableNode != null) {
									// 【分離抽出】Javadocコメントと false(式)を個別に引っこ抜く
									final PsiComment	javaComment		= PsiTreeUtil.getChildOfType(variableNode, PsiComment.class);
									final PsiExpression	newInitializerExpr	= variableNode.getInitializer();
									if (newInitializerExpr != null && javaComment != null) {
										try {
											final PsiStatement	parentStatement	= PsiTreeUtil.getParentOfType(targetExpr, PsiStatement.class);
											if (parentStatement != null && parentStatement.getParent() != null) {
												final PsiElement	statementParent	= parentStatement.getParent();
												final String		indentText		= getIndentSpace(parentStatement);
												// 親ステートメント(行)の直前に、メッセージ付きのJavadocコメントを挿入
												statementParent.addBefore(javaComment, parentStatement);
												// コメントの直後に空白行を挟む
												final PsiElement whitespace = parserFacade.createWhiteSpaceFromText(indentText);
												statementParent.addBefore(whitespace, parentStatement);
											}
											replacer.replace(targetExpr, newInitializerExpr);
										} catch(final Throwable ex)	{
											LOGGER.error("Error", ex);
										}
									}
								}
								continue;
							}
							final String	originalExprText	= baseExpr.getText();
							// 通常の置き換え
							replaceVarMeta(buf, varMatcher, originalExprText, type);
							final PsiExpression	newMethodExpr	= elementFactory.createExpressionFromText(buf.toString(), baseExpr.getParent());

							// 式ノード全体(baseExpr)を丸ごと置換
							baseExpr.replace(newMethodExpr);
							continue;
						}
					}
				}
				switch (oldName) {
					case "Job"
							-> newName	= "org.apache.hop.workflow.engine.IWorkflowEngine<org.apache.hop.workflow.WorkflowMeta>";
					case "JobMeta"
							-> newName	= WORKFLOW_META;
					case "StepMeta"
							-> newName	= TRANS_META;
					case "Trans"
							-> newName	= "org.apache.hop.pipeline.engine.IPipelineEngine<org.apache.hop.pipeline.PipelineMeta>";
					case "TransMeta"
							-> newName	= PIPELINE_META;
					case "ValueMetaInterface"
							-> newName	= "IValueMeta";
					default -> {
						continue;
					}
				}
				ref.replace(elementFactory.createReferenceFromText(newName, ref.getParent()));
			}
		}
		META: {
			final PsiClass	metaClass;
			boolean			requireAnno	= false;
			SEARCH: {
				for (final PsiClass clazz : javaFile.getClasses()) {
					final String	className	= clazz.getName();
					if (className != null && className.endsWith("Meta")) {
						metaClass	= clazz;
						break SEARCH;
					}
				}
				break META;
			}
			final var methods	= metaClass.getMethods();
			final PsiMethod	loadXmlMethod;
			SEARCH: {
				final Matcher	matcher	= PAT_LOAD_XML.matcher(EMPTY_STRING);
				for (final PsiMethod method : methods) {
					if (!matcher.reset(method.getName()).find()) continue;
					// 引数リストを取得
					final PsiParameterList	parameterList = method.getParameterList();
					int count	= parameterList.getParametersCount();
					// 引数の数が 1〜4 個の範囲内か判定
					if (count < 1 || count > 4) continue;
					// 引数の中にNode型が含まれているか走査
					for (final PsiParameter parameter : parameterList.getParameters()) {
						final PsiType	paramType = parameter.getType();
						final String	typeText = paramType.getPresentableText();
						if (typeText.equals("Node")) {
							loadXmlMethod	= method;
							break SEARCH;
						}
					}
				}
				break META;
			}
			final HashMap<String, SplitField>	fieldMap		= new HashMap<>();
			final JavaCodeStyleManager			styleManager	= JavaCodeStyleManager.getInstance(project);
			String	warning	= null;
			// 代入が発生している現場を探査
			for (final PsiAssignmentExpression assignment : PsiTreeUtil.findChildrenOfType(loadXmlMethod, PsiAssignmentExpression.class)) {
				// 右辺が XMLHandler.getTagValue(...) になっている適合パターンか調査
				final PsiExpression[]	args;
				final boolean			isAttribute;
				FIND: {
					final var	innerCalls	= PsiTreeUtil.findChildrenOfType(assignment, PsiMethodCallExpression.class);
					for (final PsiMethodCallExpression innerCall : innerCalls) {
						final String	methodName	= innerCall.getMethodExpression().getReferenceName();
						final boolean	isValue	= "getTagValue".equals(methodName);
						if (isValue || "getTagAttribute".equals(methodName)) {
							final var innerArgs	= innerCall.getArgumentList().getExpressions();
							if (innerArgs.length < 2) continue;
							isAttribute	= !isValue;
							args 		= innerArgs;
							break FIND;
						}
					}
					continue;
				}
				requireAnno = true;
				// シリアライズするときの鍵を取得
				final String	rawTagText, tagValue;
				{
					final var		theTag	= args[1];
					final Object	theObj	=
							theTag instanceof PsiLiteralExpression literal ?
									literal.getValue() :
							theTag instanceof final PsiReferenceExpression reference ?
									JavaConstantExpressionEvaluator.computeConstantExpression(reference, false) : null;
					tagValue	= theObj instanceof final String theVal ? theVal : EMPTY_STRING;
					rawTagText	= theTag.getText();
				}
				final PsiField	targetField	= getField(metaClass, loadXmlMethod, assignment);
				if (targetField == null) continue;
				final SplitField	fields;
				final String		fieldName	= targetField.getName();
				{	// 検索済なら再利用
					final var tmp	= fieldMap.get(fieldName);
					if (tmp == null) {
						final var	result	= new SplitField(targetField);
						for (final var name : result.fields.keySet()) {
							fieldMap.put(name, result);
						}
						fields	= result;
					} else {
						fields	= tmp;
					}
				}
				final PsiField	finalField;
				final String	indentText	= fields.indent;
				if (fields.isMultiple()) {
					// 1行に同居して分離していなかったら、個別に分離する
					finalField	= fields.fields.get(fieldName)
							.simplify(fileFactory, elementFactory, parserFacade);
				} else {
					finalField	= targetField;
				}

				// =================================================================
				// 【アノテーション調査と付与】
				// =================================================================
				if (finalField != null && finalField.getModifierList() != null) {
					final PsiModifierList	modifierList	= finalField.getModifierList();
					// すでに同じアノテーションがついていないか調査
					final PsiAnnotation	existingAnno	= modifierList.findAnnotation(META_PROPERTY);
					if (existingAnno == null) {
						// ついていなければ、定数付きのアノテーションノードを生成して付与
						final String	annoText;
						if (tagValue.equals(fieldName)) {
							annoText	= META_ANNOTATION;
						} else {
							annoText	= META_ANNOTATION + "(key = " + rawTagText + ')';
						}
						final var	lastAnno	= modifierList.addBefore(elementFactory.createAnnotationFromText(annoText, finalField),
										modifierList.getFirstChild());
						if (isAttribute) {
							if (warning == null) {
								warning	= "// FIXME " + I18n.message("quickfix.hop.warn.HopMetadataProperty");
							}
							final PsiComment	warningComment	= elementFactory.createCommentFromText(warning, lastAnno);
							modifierList.addAfter(warningComment, lastAnno);
							modifierList.addAfter(parserFacade.createWhiteSpaceFromText(indentText), lastAnno);
						}
						if (!indentText.isEmpty()) {
							final var	whiteSpace	= parserFacade.createWhiteSpaceFromText(indentText);
							// 読みやすさのために、アノテーションの直後に改行を少し挟む
							if (modifierList.getNextSibling() instanceof PsiWhiteSpace oldSpace) {
								oldSpace.replace(whiteSpace);
							} else {
								finalField.addAfter(whiteSpace, modifierList);
							}
						}
					}
				}
			}
			{
				// =================================================================
				// 古いメソッドを改修
				// =================================================================
				final Matcher	matcher	= PAT_META_METHOD.matcher(EMPTY_STRING);
				for (final PsiMethod method : methods) {
					final String	newName	= renameMethod(matcher, method.getName());
					if (newName == null) continue;
					method.setName(newName);
				}
			}
			IMPORT: if (requireAnno) {
				// アノーテーションクラスの追加
				final PsiImportStatement	elderImport;
				SEARCH: {
					final ArrayList<ImportSet>	sortedImports;
					{
						final var	current	= importList.getImportStatements();
						sortedImports	= new ArrayList<>(current.length);
						for (final PsiImportStatement existingImport : current) {
							final ImportSet	existing	= ImportSet.get(existingImport);
							if (existing != null) {
								if (META_PROPERTY.equals(existing.fqn)) break IMPORT;
								sortedImports.add(existing);
							}
						}
						sortedImports.sort(ImportSet::compareTo);
					}

					for (final ImportSet existing : sortedImports) {
						// 挿入したい名前が、既存の名前より前(負の値)になったら、その前に導入
						if (META_PROPERTY.compareTo(existing.fqn) < 0) {
							elderImport	= existing.importStatement;
							break SEARCH;
						}
					}
					elderImport	= null;
				}
				if (!(fileFactory.createFileFromText(
						DUMMY_JAVA, JavaLanguage.INSTANCE, "import " + META_PROPERTY + ';', false, false)
						instanceof PsiJavaFile dummyFile)) break IMPORT;
				final PsiImportList	dummyList	= dummyFile.getImportList();
				if (dummyList == null) break IMPORT;
				// コンテナの中の最初のインポート文を取得
				final var	newImport = PsiTreeUtil.getChildOfType(dummyList, PsiImportStatement.class);
				if (newImport == null) break IMPORT;
				final var	insertedImport	= elderImport != null ? importList.addBefore(newImport, elderImport) : importList.add(newImport);
				final var	newLine			= parserFacade.createWhiteSpaceFromText("\n");
				importList.addBefore(newLine, insertedImport);
			}
		}
		// 蓄積した変更を一挙に実行
		replacer.commit();
	}

	@Nullable
	private static PsiField getField(
			final PsiClass					clazz,
			final PsiMethod					loadXmlMethod,
			final PsiAssignmentExpression	currentAssign) {
		final String	fieldName	= currentAssign.getLExpression().getText();
		{
			final int	start	= fieldName.lastIndexOf('.');
			if (start >= 0) {
				final String	tmp	= fieldName.substring(start + 1);
				return clazz.findFieldByName(tmp, false);
			}
		}
		// もし代入先（targetFieldName）が「ローカル変数」だった場合の遡り追跡
		final PsiField	directField	= clazz.findFieldByName(fieldName, false);
		if (directField != null) return directField;
		// 【スコープの特定】現在の代入文が所属している、最も狭い波括弧ブロック(スコープ)を特定
		final PsiElement	currentScope	= PsiTreeUtil.getParentOfType(currentAssign, PsiCodeBlock.class, PsiMethod.class);
		// クラスのメンバー変数に見つからない = ローカルな変数(tmpなど)であると判定！
		// loadXmlMethodの内部で、「= tmp」のようにこのローカル変数を右辺に使っている別の代入文を逆探知する
		for (final PsiAssignmentExpression nextAssign : PsiTreeUtil.findChildrenOfType(loadXmlMethod, PsiAssignmentExpression.class)) {
			final PsiExpression	rExp	= nextAssign.getRExpression();
			// 次の代入文の右辺に、先ほどのローカル変数名（例: tmp）がそのまま使われているかチェック
			if (rExp != null && fieldName.equals(rExp.getText())) {
				if (currentScope != null) {
					// 次の代入文が所属しているスコープを取得
					final PsiElement	nextScope	= PsiTreeUtil.getParentOfType(nextAssign, PsiCodeBlock.class, PsiMethod.class);
					if (nextScope == null ||
							!(currentScope.equals(nextScope) || PsiTreeUtil.isAncestor(currentScope, nextAssign, false))) {
						continue;
					}
				}
				final PsiField	nextField	= getField(clazz, loadXmlMethod, nextAssign);
				if (nextField != null) return nextField;
			}
		}
		return null;
	}
	/** 直前の空白を引用する */
	private static @NotNull String getIndentSpace(final PsiElement parentStatement) {
		final PsiElement	prevSibling	= parentStatement.getPrevSibling();
		if (prevSibling instanceof PsiWhiteSpace) {
			final String	prevText	= prevSibling.getText();
			// 改行(\n)以降に含まれる、純粋なインデント用の空白(スペースやタブ)だけを抽出
			final int	lastNewLine	= prevText.lastIndexOf('\n');
			return lastNewLine >= 0 ? prevText.substring(lastNewLine) : '\n' + prevText;
		} else {
			return INDENT_TEXT;
		}
	}

	/** @param	matcher	{@link #PAT_META_METHOD}で作成した{@link Matcher}
	 * @return	該当なしの場合にnullを返す */
	@Nullable
	private static String renameMethod(final Matcher matcher, @NotNull final String methodName) {
		if (!matcher.reset(methodName).find()) return null;
		final StringBuilder	buf		= new StringBuilder(methodName.length() + 10);
		final int			end		= matcher.end(1);
		final String		type;
		switch (methodName.charAt(end)) {
			case 'S':
				type	= TRANSFORM;
				break;
			case 'T':
				type	= PIPELINE;
				break;
			default:
				return null;
		}
		buf.append(methodName, 0, end).append(type).append(methodName, matcher.end(), methodName.length());
		return buf.toString();
	}

	private static void replaceVarMeta(final StringBuilder buf, final Matcher varMatcher, final String oldName, final char type) {
		final int			end	= varMatcher.end();
		buf.append(oldName, 0, varMatcher.start()).append("get")
				.append(switch(type) {
					case 'j'	-> "Workflow";
					case 's'	-> TRANSFORM;
					default		-> PIPELINE;
				}).append(oldName, varMatcher.start(1), end)
				.append("()").append(oldName, end, oldName.length());
	}
	private static boolean isUpper(final String str) {
		return !isEmpty(str) && Character.isUpperCase(str.charAt(0));
	}

	private static class ReplaceAll implements ThrowableRunnable<IncorrectOperationException> {
		private final @NotNull Project		project;
		private final Document				document;
		private final PsiJavaFile			javaFile;
		private boolean						stay	= true;
		private ReplaceAll(final @NotNull Project project, final PsiJavaFile original, final PsiJavaFile javaFile) {
			this.project	= project;
			this.javaFile	= javaFile;
			document		= PsiDocumentManager.getInstance(project).getDocument(original);
		}

		private void commit() throws IncorrectOperationException {
			if (document == null || stay) return;
			WriteCommandAction.writeCommandAction(project, javaFile)
					.withName("Replace Multiple Imports").run(this);
		}
		private PsiElement replace(final PsiElement from, final PsiElement to) {
			if (to == null) return null;
			stay	= false;
			return from.replace(to);
		}
		@Override
		public void run() throws IncorrectOperationException {
			document.setText(javaFile.getText());
		}
	}
	private record ReplaceSet(PsiElement from, PsiElement to) {}
	private static class ImportSet implements Comparable<ImportSet> {
		@NotNull
		final String				fqn;
		final PsiImportStatement	importStatement;
		@Nullable
		private static ImportSet get(final PsiImportStatement importStatement) {
			final String	fqn	= importStatement.getQualifiedName();
			return fqn != null ?  new ImportSet(fqn, importStatement) : null;
		}
		private ImportSet(@NotNull final String fqn, final PsiImportStatement importStatement) {
			this.fqn				= fqn;
			this.importStatement	= importStatement;
		}
		public String toString() {
			return fqn;
		}

		@Override
		public int compareTo(final @NotNull QuickFixImport.ImportSet o) {
			return fqn.compareTo(o.fqn);
		}
	}
}