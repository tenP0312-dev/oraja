# 実効LN種別とランキングのノーツ数

Source implementation: Issue #485。paired plugin: BMS-Mania/bms-ir-plugin#65。
paired server: BMS-Mania/IR#1416。配布・本番反映は別作業。

`ScoreData.mode` は既存の保存キーの意味を維持する。今回のプレイの実効種別は
`bmsirEffectiveLnType`（0=LN、1=CN、2=HCN）へ別に記録し、IRScoreData.lntypeへ渡す。
混在譜面は実際に含む種別の最大値で区分する。未定義ノートは選択mode、明示ノートは
その種別、Force LNはすべてLNとなる。新しい自己ベストとプレイ履歴へ種別を保存する。
旧DBへ追加する列の既定値は-1（不明）。旧記録をnotesやMAXから推測して分類しない。
自己ベストの実効種別は今回のEX代表行に更新したときだけ更新する。

IRScoreData.bmsirScoreModeは元の保存modeを保持し、pluginの補助DB検索に用いる。
再送キューは譜面、実効種別、notes、ステージ実績のIRスナップショットを作成時に固定する。
後の設定変更や次のプレイを元の送信条件へ流用しない。

選曲workerは音源/BGAをロードせず譜面だけを読み、選択された解釈でノーツ数を数える。
catalogのCNノーツ数をForce LNのノーツ数として使わない。読めない・ハッシュが変わった
譜面は不明として扱い、対応pluginは推測値で要求しない。BMS入力の上限は16MiB。
既存のarchive resourceとbmson decoderを使い、元譜面・プレイモデルは変更しない。
NANTOKAのロード済みモデルには既存の実計数を使用する。

対応plugin向け任意拡張は `bmsirRankingLnType` と `bmsirRankingNotes`。
pluginは正の実ノーツ数をtotalnotes、拡張識別をranking_contract=1として送る。
通常LNも対応サーバーが指定群を選び、該当群がなければ正常0件を返す。
応答・ディスク復元・last-good・ライバル書戻し・リザルト・Primary IR一覧で、種別と
notesが一致するか確認する。不一致応答では正常0件として上書きせず、取得失敗として扱う。

永続cache formatは2。旧形式は再取得し、実効種別/notesをworkerの保存identityへ含める。
既存の受信scope、ON/OFF分離、single-flight、通常TTL、再試行、last-good保持は維持する。
旧pluginは任意フィールドを無視するが、本体もノーツ数不一致を受け入れない。
対応pluginは旧serverのlntype欠落互換を保持しつつnotesを検査する。

検証: IRLongNoteContractTest、既存Force LN/保存/ランキング回帰テスト。
設定ON/OFF・通常/混在bmson・旧DB・再送・選曲/リザルト/再起動後の実機受入はoperatorが行う。
