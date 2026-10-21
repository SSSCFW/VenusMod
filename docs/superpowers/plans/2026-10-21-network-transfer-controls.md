# 診断GUIの転送操作 Implementation Plan

> superpowers:executing-plans。承認済みNETWORK_SYSTEM_SPECのGUI診断/QoSの継続。

**Goal:** 診断GUIから自機の転送の優先度変更と確認付き停止を行う。
**Architecture:** 同じ診断パネルに転送操作ページを設け、IDと優先度の下書き/停止確認を純Javaモデルで管理。既存CLI/Command Payload/サーバー認可のみを再利用し、保存形式や転送エンジンは変更しない。
**Tech Stack:** Java21 / Minecraft1.21.1 / NeoForge21.1.235 / optional JEI19.21.0.247。
**Spec:** docs/NETWORK_SYSTEM_SPEC.md のGUI/診断/QoS、docs/NETWORK_TRANSFER_QOS.md。

## Constraints / Review Focus
- feature/venus-hostile-mobsと既存workflowのみ。main/新規branchは変更しない。
- 保存v16、network-3、136種の4/16幅ケーブル、画像/モデル/レシピを維持。
- IDは正のlongだけ。改行/命令混入/overflowを拒否。宛先/転送量の誤入力は操作IDの処理へ影響させない。
- 停止の最初のクリックは送信しない。対象編集、ページ離脱、リサイズで確認を取り消す。二重クリックの誤確定を抑止する。
- 別の送信元/権限外/閉じたメニュー/完了済みIDをサーバーで拒否。クライアント表示を成功の根拠にしない。
- クライアントの下書きだけを保持し、自動ポーリング/転送の自動再開/全停止を追加しない。

## Tasks
- [x] 1. 未実装モデルのRED、変換/確認/同一機器だけの変更をGREENで検証。
- [x] 2. 診断GUIのページ追加、既存ガイド/JEI説明、実メニューGameTestとネイティブ入力検査。
- [ ] 3. 全純Java回帰、アセット参照、自己レビュー、変更blobの照合、通常push。
- [ ] 4. 既存CIのBuild/GameTest/実JEI/画像/JAR公開を確認し、結果を記録。

## Ledger
- 開始head 2e543cf。成功CI8c99209の入力を独立したsnapshotへ展開。live compareで差分は検証文書2件のみと確認。変更ファイルだけを最新treeへ重ねる。
- 診断の既存画面を狭くせず、転送操作ページへ切り替える構成。一覧は手動更新、IDは一覧から入力する。構造化一覧payloadの新設は今回行わない。

- RED: TransferControlDraft未実装でコンパイル失敗。GREEN: 新規84検査、全101クラス成功。資源1,594 JSON/204 PNGは全参照解決。新GameTestと実入力は追加済み、CI結果はまだ未確認。
