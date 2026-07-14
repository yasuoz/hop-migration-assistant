package jp.ac.nitech.cc.hop.assistant;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.InheritanceUtil;
import jp.ac.nitech.cc.hop.assistant.service.HopProjectSettingsService;
import org.jetbrains.annotations.NotNull;

import static jp.ac.nitech.cc.hop.assistant.constructor.HopConstructorInspection.DIALOG;

/**
 * Hopライブラリが有効なプロジェクトにおいて、
 * Dialog系クラス(未使用と誤判定されがち)の未使用フラグを自動で解除するプロバイダー。
 * バックグラウンドで常時稼働するため、極限までフラットかつ高速に判定します。
 */
public class HopImplicitUsageProvider implements ImplicitUsageProvider {

	@Override
	public boolean isImplicitUsage(@NotNull final PsiElement element) {
		// パターンマッチング付きの instanceof で、PsiClass へのキャストと判定を1行で高速処理
		if (!(element instanceof final PsiClass psiClass)) {
			return false;
		}

		// プロジェクトのサービスから Hopライブラリの有無を判定（Kotlinの service<T>() を Javaの getService に変換）

		final var	settings	= psiClass.getProject().getService(HopProjectSettingsService.class);
		if (settings == null || !settings.getHasHopLibrary()) {	// 自動生成される安全なゲッター
			return false;
		}

		// 抽象クラスやインターフェースは元々グレーアウトしないので、ここで最速早期リターン
		if (psiClass.isInterface() || psiClass.hasModifierProperty("abstract")) {
			return false;
		}

		// クラス名を取得し、Nullチェックと接尾辞（Dialog）の高速前方/後方一致チェック
		final String className = psiClass.getName();
		if (className == null || !className.endsWith(DIALOG)) {
			return false;
		}

		// 最後に、重い処理である「クラスの継承関係（InheritanceUtil）」のチェックを評価
		// 前方のプリミティブなチェックで9割以上ハネられているため、CPU負荷を最小限に抑えられます
		return InheritanceUtil.isInheritor(psiClass, Fixer.ACTION_DIALOG.interfaceClass)
				|| InheritanceUtil.isInheritor(psiClass, Fixer.TRANS_DIALOG.interfaceClass);
	}

	@Override
	public boolean isImplicitWrite(@NotNull final PsiElement element) {
		return false;
	}

	@Override
	public boolean isImplicitRead(@NotNull final PsiElement element) {
		return false;
	}
}