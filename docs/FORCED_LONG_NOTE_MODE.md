# 選択LNモードの強制適用

Issue #341 / paired BMS-Mania/bms-ir-plugin#41。
2026-09-05のBMS-Mania/IR#478要望により、#210の「明示CN/HCNは保持する」
プレイ仕様を変更する。parser自体の仕様は変えない。

## 適用

- 通常プレイ・オートプレイ・練習・コース・再読込は選択中の0=LN、1=CN、2=HCN。
- Arenaは読込前に固定されたモード。ratedはLN、casual/privateはルームのモード。
- BMSの`#LNMODE`、LN専用チャンネル、`#LNOBJ`、bmsonの`ln_type`とノートごとの`t`を
  読み込んだ後、既存の全ロングノートを選択モードへ統一する。ノートの時刻、レーン、
  ペア、キー音は変えない。普通のノートや地雷をLNへ追加変換しない。
- 判定・描画・ノーツ数・IR DTO・Arena readyのノーツ数が同じモデルを参照する。
  LN終端はノーツ数に含めず、CN/HCN終端は含める。
- 「強制LN／ロングノート変換」と表示されるLong Note Modify Modeは従来の
  ADD/REMOVEオプションであり、このモード選択とは別。ArenaはADD/REMOVEを無効にする。
- 譜面ファイル、MD5/SHA-256、通常のIR `lntype=0/1/2`契約は維持する。

## ローカル成績・リプレイの互換性

- 未定義LNのみの譜面・LNなし譜面は既存の成績を引き継ぐ。
- 明示LN/CN/HCNを含む譜面は、旧成績を新しい強制モードの自己ベストへ流用しない。
  新成績には`score.bmsirLongNotePolicy=1`を保存し、LN/CN/HCNを既存のmodeキーで分ける。
- 初回の新成績保存で同じsha256/modeに旧成績が存在する場合、旧行の全列をJSONで
  `bmsir_legacy_ln_score(sha256, mode, snapshot)`へ退避してから新成績を保存する。
  両処理は同じSQLiteトランザクション。失敗時は退避も更新もrollbackする。
  起動時に既存の成績を一括変換しない。旧行の退避は再度上書きしない。
- 旧policyのレコードで新policyの成績を置換しない。選曲・プレイ・コースの
  読み出しもpolicyを確認する。旧成績の参照用JSONは通常の成績一覧やIR再送対象にしない。
- IRScoreDataの任意拡張`bmsirLongNotePolicy`を使い、paired pluginは送信状態と
  best-fieldsの保存キーを旧成績から分ける。元の譜面ハッシュと送信modeは変えない。
- 新リプレイは`bmsirForcedLongNotes=true`と選択modeを保存する。
  明示LN種別を含む譜面では、replay専用の派生保存キーを使用し、旧ファイルと
  新しいLN/CN/HCNファイルの誤探索・上書きを防ぐ。譜面そのもののハッシュは変えない。
  フラグを持たない旧リプレイは譜面の明示指定を保持してdecodeし直す。
  新しいリプレイがない場合は、元の譜面指定とmodeに対応した従来の保存先も探す。
  #RANDOMは記録された分岐を用いる。スロット保存操作自体は従来どおり上書きする。

## 配布条件

この変更はsource-only。新版pluginと本体を同時に配布する。
配布時はArenaの本体許可ゲートで新旧LN解釈の本体が同じ対戦へ入らないようにする。
protocol v8のmode/totalnotes合意だけでは、ノーツ数が等しいCNとHCNの差を検出できない。
旧本体へのrollbackでは対応する旧本体ゲートへ戻し、新旧混在状態を作らない。
本番ゲート変更、配布、実機確認、一般公開告知は別フェーズとして記録する。
旧実装は新しいlocal score policyを理解しないため、旧本体での通常プレイrollbackには
更新前のplayerディレクトリのバックアップも必要になる。

## 回帰確認

- 全BMS `#LNMODE=0/1/2/3` × LN専用チャンネル/`#LNOBJ` × 選択LN/CN/HCN。
- bmsonのヘッダ指定とノート個別指定が混在する譜面。
- ペア・キー音・時刻・譜面ハッシュを保持し、LN/CN/HCNのノーツ数とIR DTOが一致する。
- #RANDOMの固定分岐、旧/新リプレイの保存・探索、invalid mode拒否。
- Arenaの固定モードが通常設定と譜面のHCN指定を上書きする。
- 旧DBの列追加、旧成績の除外・退避、新旧上書き防止、強制失敗時のatomic rollback。
- `./gradlew core:test -Dplatform=macos -Darch=aarch64` とmacOS body build。
- paired pluginのUploadSnapshotHarnessを旧hostと新hostで実行する。
- 実機では通常・rated・各casual/privateモードの判定/描画、CN終端、HCN中断/回復、
  練習/quick retry/コース、旧リプレイ、IRとArenaの結果をoperatorが確認する。
