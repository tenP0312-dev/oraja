# Arena oraja 0.4.14.117 任意test

0.4.14.116の全配布機能を保持し、本体PR #486の実効LN種別・ノーツ数契約だけを追加する。
plugin0.0.81は0.0.80のFHS機能を保持してpaired plugin PR #66を追加する。
launcher0.2.30、stable、mandatory、NONSTOP MIXの既存サーバー未対応表示は維持する。
管理Issue: oraja #541、IR #1438。PRのmainマージは2026年11月以降へ保留する。

## 変更

- 強制LNの単曲・コースランキングは実際のLN種別とノーツ数で取得・検証する。
- ノーツ数が違う応答や旧cacheを採用せず、正常0件と取得失敗を区別する。
- 投稿用の実LN種別と既存ローカル保存modeを分け、再送に必要な情報を固定する。
- 旧成績の実種別は不明のまま保持し、推測して再投稿・移行しない。
- 0.4.14.116のNONSTOP MIX、手元カメラ、FHS等の機能を保持する。

## 配布検証

確定commitから両OSの本体と対応source ZIPを作り、全体テスト、release tools、
feature parity、両方の実物hostに対するplugin完全build・全harnessを確認する。
hosted plugin CIの課金制限と実物の検証結果は分けて記録する。
署名/history/artifact監査、通常IR/Arena gate、必要なguarded reload、healthと
known/unknown WSSを通してからsigned testチャンネルへ反映する。
本番成績の作成・修復、一般告知、GUI操作は行わない。実機受入はoperatorが行う。
