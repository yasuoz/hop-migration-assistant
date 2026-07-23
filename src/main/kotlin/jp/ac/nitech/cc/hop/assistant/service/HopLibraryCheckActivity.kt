package jp.ac.nitech.cc.hop.assistant.service

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.startup.ProjectActivity

/** クラスパスにHop向けクラスがあるか1度だけ確認 */
class HopLibraryCheckActivity : ProjectActivity {
	override suspend fun execute(project: Project) {
		// プロジェクトのインデックス作成・初期化(Smartモード)が完了するまで安全に待つ
		DumbService.getInstance(project).waitForSmartMode()

		// ディスパッチャの競合を避けるため、安全な読み込みアクションを実行
		val hasLibrary = readAction {
			var found = false

			// プロジェクト全体のライブラリ(Gradleが持ってきたJARなど)を走査
			OrderEnumerator.orderEntries(project).librariesOnly().forEachLibrary { library ->
				val urls	= library.getUrls(OrderRootType.CLASSES)
				for (url in urls) {
					// 例: "jar:///path/to/hop-core-**.jar!/" のような形式から判定
					if (url.contains("hop-core-") && url.endsWith(".jar!/")) {
						found	= true
						return@forEachLibrary false // 見つかったら走査を即座に終了 (falseを返すとブレイク)
					}
				}
				true	// 次のライブラリへ進む
			}
			found
		}
		project.service<HopProjectSettingsService>().hasHopLibrary	= hasLibrary
	}
}