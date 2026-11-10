# Arena oraja 0.4.14.121 任意test

0.4.14.120の全配布機能を保持し、マージ済みPR #573だけを取り込みます。
管理Issueは #575、サーバー側の配布ゲート・live確認は BMS-Mania/IR#1469。
plugin 0.0.81、launcher 0.2.30、stable、mandatoryは維持します。

## 変更

- スキン読み込み時に同じLuaコードを再コンパイルする一時割り当てを減らします。
  件数・容量を制限してコンパイル結果だけを再利用し、最新ソースは毎回確認します。
- Luaスキン定義の変換に使う型情報を再利用し、要素ごとのreflection割り当てを減らします。
- Luaの可変table・実行環境・upvalue・callback結果はスキン間で共有しません。

非GUIの対象処理計測では、Lua再読込44.34→7.06MiB/回、合成定義変換
16,602,952→3,743,752bytes/回。これはゲーム全体のメモリ削減率ではありません。
発生開始が119か120かは未確定。実際のメモリ使用量・カクつき改善は #574 の操作者確認に残します。

## Optional test scope (English)

Preserves every 0.4.14.120 feature and adds only merged #573 to reduce temporary
Lua skin-loading allocations through bounded compiled-code reuse and reusable
reflection metadata. Mutable tables, environments, upvalues and callback
results remain independent. Plugin 0.0.81, launcher 0.2.30 and optional-update
policy are unchanged. CPU loading measurements do not establish process-memory
or gameplay-stutter improvement; operator acceptance remains in #574.

## 配布検証

exact commitの両OS本体と対応source ZIP、対象テスト・release tools・feature parity・
全体CI、同一pluginの互換性、署名/history/artifactを検証します。
通常IR/Arenaゲート、zero-use・rollback・guarded reload・health・known/unknown WSSを
完了後にsigned testチャンネルへ反映し、ライブ一致とdeployment markerを確認します。
ゲーム・ランチャーの起動やGUI操作による実機検証はCodexでは行いません。
