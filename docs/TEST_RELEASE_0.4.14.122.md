# Arena oraja 0.4.14.122 任意test

0.4.14.121の全配布機能を保持し、マージ済みPR #579（#578）と #580（#577）だけを追加します。
管理Issueは #582、サーバー側の配布ゲート・live確認は BMS-Mania/IR#1472。
plugin 0.0.81、launcher 0.2.30、stable、mandatoryは維持します。

## 変更

- F5でスキン設定を開く際に、稼働中スキン全体の再読み込みを省きます。
  F5/F12のヘッダ一覧を共用し、設定値は独立したコピーを渡します。
  skin配下の変更を検出し、F5の「一覧を更新」で外部依存も再取得できます。
  F12プレビューは最初の画面を表示してから読み込みます。
- NANTOKA v2のBSS切り返しで、終端と同時刻の追加押下が次の通常皿・CN・HCNを
  巻き込まないようにします。別時刻・他レーンの判定、通常判定とNANTOKA v1は維持します。

スキンの初回/再表示の待ち時間・描画・操作は #581、BSSは実コントローラーによる
切り返し・後続皿・CN/HCN・旧リプレイを操作者が確認します。GUIの起動・操作はCodexでは行いません。

## Optional test scope (English)

Preserves every 0.4.14.121 feature and adds only merged #579 and #580.
F5 avoids reloading the active skin; F5/F12 reuse isolated header definitions,
detect skin changes, support explicit refresh and defer the F12 preview until
the first complete frame. NANTOKA v2 scratch reversals judge only the BSS
endpoint at the same input time, preserving following scratches and CN/HCN.
Ordinary judgment and NANTOKA v1 are unchanged. Plugin 0.0.81, launcher 0.2.30
and optional updates remain. Physical menu/controller acceptance is pending.

## 配布検証

exact commitの両OS本体/source ZIP、対象テスト・最終候補CI・release tools・feature parity、
同一pluginの互換性、署名/history/artifactを検証します。zero-use・rollback、通常IR/Arenaの
全加算ゲート、必要なguarded reload、全health、known/unknown WSSを完了後にsigned testへ
反映し、公開8ファイル一致とdeployment markerを確認します。
