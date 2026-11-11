# Arena oraja 0.4.14.123 / plugin0.0.82 任意test

0.4.14.122の全配布機能を保持し、oraja #571、parser tenP0312-dev/jbms-parser#3、
plugin BMS-Mania/bms-ir-plugin#70の孤立LNOBJ・成績identity修正を追加します。
配布管理は #584、通常IR/Arenaゲート・本番確認は BMS-Mania/IR#1473、
署名test配布は tenP0312-dev/bms-ir-arena-patch-server#73で追跡します。
launcher0.2.30、stable、mandatoryは維持します。

孤立LNOBJを通常ノートとして保持し、公開MD5/SHA-256を変えず実ノーツ数を修正します。
新解釈の成績・リプレイは旧解釈と分離し、旧PBの推測移行は行いません。
revision1/2リプレイの読み取りを保持します。RANDOM未解決時はランキングを推測せず、
単曲・コースの実モデルと投稿identity・LN種別・ノーツ数を照合します。
plugin0.0.82は不一致投稿を通信前に拒否し、補助DB・投稿状態もノーツ数ごとに分離します。

操作者は本番反映を先行し、残るPRのマージを2026年11月へ延期するよう明示しました。
課金制限で起動できないCIは成功扱いにせず、配布ソースはcommit/pushした未マージPRで保持します。
exact-source両OS本体/source ZIP、全体テスト、plugin13 harness、署名/history/artifact、
zero-use・rollback・通常IR/Arena加算ゲート・guarded reload・全health/WSS・live一致を確認します。

1433ノーツ群の過去成績修復・再分類、一般告知、実ゲーム/launcher操作は含みません。
実機でのランキング、LN/CN/HCN・強制LN、旧リプレイ、単曲・コース投稿確認はoperatorが行います。

## Optional test scope (English)

Preserves all 0.4.14.122 features. Parser revision 3 retains orphan LNOBJ notes
without changing public chart hashes, separates changed local records and keeps
revision 1/2 replay decoding. Body #571 and plugin #70 align played models,
effective LN types, note counts and local score identities for singles/courses.
Plugin 0.0.82 refuses mismatched uploads before HTTP and isolates auxiliary data
and upload state by note scale. Launcher 0.2.30 and optional updates remain.
Merge is deferred to November 2026 by the operator; unavailable billing-limited
CI is recorded honestly. Historical scores are not migrated. Physical acceptance
remains operator-run.
