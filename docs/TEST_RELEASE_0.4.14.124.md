# Arena oraja 0.4.14.124 任意test

0.4.14.123の全配布機能を保持し、PR #588（#587）の旧カタログ・自己ベスト参照の更新と、
PR #591（#590）の NANTOKA MANIA 記録の任意外部ランキングサーバー送信を追加します。
配布管理は #592 で追跡します。plugin0.0.82、launcher0.2.30、stable、mandatoryは維持します。

新パーサーでPBを保存した後、旧カタログと自己ベストの参照を更新し、選択済みRANDOM分岐を保持します。
プレイヤーフォルダの `bmsir_nantoka_ir.json` で外部サーバーのAPIベースURLとトークンを指定した場合だけ、
NANTOKA MANIA単体のプレイ・段位の合否・手元の自己ベストを送ります。設定がなければ何も送らず、
BMS-IRへの通常送信とArenaの動作は変わりません。

操作者の指示により、#591 はマージせず push 済みのPRブランチを配布ソースとします。
exact-source両OS本体/source ZIP、関連テスト、署名/history/artifact、
zero-use・rollback・通常IR/Arena加算ゲート・guarded reload・全health/WSS・live一致を確認します。

実ゲーム/launcherでの確認、一般告知は含みません。#589 の実機確認は operator が行います。

## Optional test scope (English)

Preserves all 0.4.14.123 features. Adds PR #588 (#587), which refreshes legacy
catalog and personal-best references after parser PB saves while keeping the
selected RANDOM branch, and PR #591 (#590), which sends NANTOKA MANIA plays,
Dan results and local bests to an external ranking server only when the player
configures its API base URL and token. Without that file nothing is sent and
BMS-IR submission and Arena behavior are unchanged. #591 is distributed from
its pushed, unmerged branch by operator decision. Plugin 0.0.82 and launcher
0.2.30 remain. Physical acceptance remains operator-run.
