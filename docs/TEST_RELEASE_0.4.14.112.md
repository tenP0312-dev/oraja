# Arena oraja 0.4.14.112 任意test

0.4.14.111の配布系列を保ち、フローティングハイスピード使用の印（#515）だけを追加します。
本体の更新は任意、Arenaプラグインは0.0.80、ランチャー0.2.30は保持します。
stableとmandatoryは変更しません。配布管理はoraja #517です。

## 追加内容

- プレイ中に一度でもフローティングハイスピードが有効だったスコア（開始時・途中切替の
  どちらも。コースはどれか1曲）を、IR送信時に任意項目 `floating_hispeed` で知らせます。
  BMS-IRのWeb OP欄に `FHS` が付きます（サーバーはBMS-Mania/IR#1424で反映済み）。
- 表示だけの印です。判定・ランプ・スコア・リプレイ・Arenaの判定は変えず、
  ローカルのスコアDBにも保存しません。古い本体・既存スコアには何も付きません。

## 検証・配布条件

本体テスト、両OSビルド、source ZIP、feature parity、プラグイン0.0.80の互換ハーネスを
確認します。署名状態・更新履歴・artifact identityを検証し、ordinary body／pluginと
Arena version／build gate、必要なguarded reload、health、known/unknown WSSを確認した後に
testチャンネルへ反映します。

実機でのFHS表示・切替操作は運用者が確認します。自動テストの成功を実機確認の完了とは
扱いません。
