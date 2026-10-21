# 転送操作GUIの検証

開始headは `2e543cfeb3b1541ab617f60326141f787ec17ad5`。成功CI `8c99209` の入力snapshotと開始headとの差分が検証文書2件のみであることをlive GitHubで確認し、独立したディレクトリで変更しました。

## 純Java・資源

TransferControlDraftがない状態で新規テストのコンパイル失敗を確認した後に実装しました。TransferControlDraftTestは84検査に成功。IDの書式/long上限/命令混入拒否、停止確認の消費・取消、下書き保持、既存CLIを通した別送信元の拒否と自機だけの変更・停止を検査します。

既存を含む101テストクラスをJava21でコンパイルし全成功。既存NetworkDiagnosticsTestの91検査も維持。生成アセットの1,594 JSON・204 PNGは全参照解決し、生成器・既存画像・モデル・レシピは変更していません。

## 実配線・画面

NetworkTransferControlGameTestsを1件追加しました。実際の青色ケーブル、正規の機器メニュー、所有者/権限外、別送信元、閉じたメニュー、停止済みID、設定・装着品の保持を検査します。

既存のJEI実入力スモークへ優先度変更、ID誤入力、停止の保留/中止、ID変更・リサイズ・ページ移動での確認取消、確定後の停止を追加しました。GUIイベントを直接呼ばず、同じ隔離Xvfb上のRobotから入力します。13-transfer-priority / 14-transfer-confirmation / 15-transfer-stoppedの3画面を追加しました。

この初回文書時点ではMOD全体ビルド・新GameTest・実入力の完了結果は未確認です。既存CIの実行ログと画像を取得して追記します。

## 範囲

人による操作、IME入力、全GUI倍率・シェーダー、実マルチプレイ、大規模TPS測定は未実施です。独立したサブエージェントは使用せず自己レビューを実施。参考はNeoForge1.21.1のScreens公式説明（https://docs.neoforged.net/docs/1.21.1/gui/screens/）。
