# Arena oraja 0.4.14.118 任意test

0.4.14.117 の全配布機能を保持し、main にマージ済みの次の2件だけを追加します。
plugin 0.0.81、launcher 0.2.30、stable、mandatory は維持します。
管理Issueは oraja #548 です。

## 変更

- 譜面DL・F2のフォルダ更新・差分D&Dで、旧パーサーのメタデータが残っていても、
  songdb の全行を削除しなくなりました。古い行は走査中のファイルごとに
  再解析して上書きします（#544、#545）。
- 自己ベストを更新した送信の後、最初のランキングをすぐ表示します。
  そのうえで、サーバーのspool反映を待って4.5秒後に1回だけ再取得します（#546、#547）。
  0.4.14.117 の一致チェック付きの取得処理に移植しています。
- 既に消えた利用者のDBは修復しません。全件読み直しが必要です。

## 配布検証

確定commitから両OSの本体と対応source ZIPを作り、全体テスト・release tools・
feature parity・CIを確認します。plugin は 0.0.81 の同一JARを再利用します。
署名/history/artifact監査、通常IR/Arena gate、guarded reload、health、
known/unknown WSSを確認してから signed test チャンネルへ反映します。
実機での受入確認はoperatorが行います。
