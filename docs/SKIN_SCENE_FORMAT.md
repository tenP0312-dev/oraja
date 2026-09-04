# Skin Scene format v1

Skin Sceneは、従来のJSON/Lua skinへ親子変換、3D affine、focal camera、mesh、
色変換、独立timeline、hard maskを追加する任意の中立resource形式です。sceneを
使わないskinは従来の`SpriteBatch`経路だけを通ります。

## Skinからの参照

JSON skinでは既存の数値`scene`（画面の表示時間）と名前が衝突するため、resource
配列名は`scenes`です。Lua skinも同じreflection schemaを使います。

```json
{
  "scene": 2500,
  "scenes": [
    {
      "id": "projective-background",
      "path": "scene/projective-background.scene.json",
      "timer": 0,
      "cycle": 2500,
      "playbackRate": 1.0,
      "loop": true
    }
  ],
  "destination": [
    {
      "id": "projective-background",
      "dst": [{"time": 0, "x": 0, "y": 0, "w": 1920, "h": 1080}]
    }
  ]
}
```

sceneは1個の通常`SkinObject`としてdestination順へ挿入されます。rootの矩形、色、
alpha、回転、offset、draw条件は既存destinationの規則に従います。scene idが既存
resource idと重複した場合、またはsceneのloadに失敗した場合はそのdestinationだけを
無効にし、同名の画像などへ暗黙にfallbackしません。

`cycle`はミリ秒で、0ならdocumentの`duration`を使います。`timer`を省略したsceneは
state開始から進みます。時刻は描画フレームごとの絶対microsecondから計算され、skinの
prepare FPS設定では間引かれません。

## Document

```json
{
  "formatVersion": 1,
  "canvas": {"width": 1920, "height": 1080, "origin": "top-left"},
  "timebase": {"ticksPerSecond": 60000},
  "duration": 150000,
  "textures": [],
  "meshes": [],
  "clips": [],
  "rootClip": "root"
}
```

- tickは整数です。`ticksPerSecond: 60000`では60fpsの1 frameが1000 tickです。
- `origin`は`bottom-left`または`top-left`です。Y反転はrootで一度だけ行われます。
- 未知のfield、未知の`formatVersion`、NaN/Infinity、不正参照はload errorです。
- texture pathはdocumentと同じdirectory以下の相対pathだけを許可します。symlinkを
  解決した実pathも同じroot以下でなければなりません。

完全な自作例は
[`examples/skin-scene/projective-grid.scene.json`](../examples/skin-scene/projective-grid.scene.json)
にあります。

## Textureとmesh

textureは`id`, `path`, `filter` (`nearest`/`linear`), `wrapX`, `wrapY`
(`clamp`/`repeat`), `premultipliedAlpha`を持ちます。同じtextureはscene内で共有され、
最後の参照をdisposeしたときに1回だけ解放されます。

meshは`positions`（xyz）、`uv`、triangle `indices`を持ちます。外部配列の行列は
row-majorですが、mesh positionはscene pixel単位です。meshを省略したtexture leafは
`0..1`の共有quadを使います。16-bit index batchのため、1 meshは65532頂点・196596
index以下です。UVの`(0,0)`は読み込んだ画像の先頭（上側）を指します。

## Clipとinstance

clipは`id`, `duration`, `framesPerSecond`, `instances`を持ちます。instanceは次を
指定できます。

- `kind`: `texture`, `mesh`, `clip`
- `ref`: textureまたは子clipのid。`mesh` leafではtexture id
- `mesh`: 任意mesh id
- `depth`: 昇順。同値はdocument記載順
- `start`, `end`: `start <= tick < end`の半開区間
- `blend`: `normal`, `add`, `subtract`, `multiply`, `screen`, `erase`, `invert`
- `uvRect`: `[u0,v0,u1,v1]`
- `transformTrack`, `colorTrack`, `cameraTrack`, `clipRectTrack`
- 子clip用の`startOffset`, `playbackRate`, `loop` (`none`/`repeat`), `frameTrack`

clip参照は循環できません。instance idはdocument全体で一意です。子clipの通常時刻は
親のactive開始から連続計算され、`frameTrack`のactive区間だけ指定frameへstep固定
されます。

## Transform

`transformTrack.kind`は次の3種類です。

- `affine2`: `[a,b,c,d,tx,ty]`。`x'=a*x+c*y+tx`, `y'=b*x+d*y+ty`
- `affine3`: row-major 3x4
  `[m00,m01,m02,tx,m10,m11,m12,ty,m20,m21,m22,tz]`
- `trs`: `translation`, quaternion `rotation` (x,y,z,w), `scale`, `pivot`

`step`は前keyを保持し、`linear`は行列成分をlerpします。TRS trackの`trs`補間は
translation/scale/pivotをlerpし、quaternionを最短経路slerpします。pivotは
`Translate(pivot) * transform * Translate(-pivot)`です。親子は
`world = parentWorld * local`で合成します。省略されたkey値は前keyから継承します。

## Camera、色、mask

cameraは`orthographic`または`focal`です。focal cameraのcenterを`(cx,cy,cz)`、
焦点距離を`f`とすると、`scale=f/(z-cz)`で投影します。camera後方のprimitiveは
安全に除外します。shaderはclip-space Wを維持するためUVはGPUのperspective-correct
補間になります。

color keyは`mul` (RGBA), `add` (RGBA), `hsl`を持ちます。処理順は
`texture -> HSL shift -> mul/add -> blend`です。hueは1.0で一周し、saturationと
lightnessは適用時にclampします。親子のaffine colorは
`worldMul=childMul*parentMul`, `worldAdd=childAdd*parentMul+parentAdd`です。

`clipRectTrack.rect`はlocal `[x,y,width,height]`です。screen軸平行ならscissor、
回転・透視・入れ子ならstencil stackを使います。通常backbufferとSkin Select preview
FBOはいずれもstencil attachmentを要求します。pass終了時はblend equation/function、
color mask、stencil、scissorを従来値へ戻します。scene shaderは既存の互換変換を通し、
通常のGLSL 1.20経路とmacOSのcore-profile GLSL 1.50経路の両方で生成されます。

## 検証上限と診断

上限は`SceneCompiler`へ集約されています。初期値はtexture 512、mesh 2048、clip
1024、instance/展開command 16384、key 1000000、再帰64、mask深度8です。textureは
GPU最大寸法を確認し、推定decode量が256 MiBを超えるsceneを警告します。

debug表示にはscene evaluation microseconds、active command数、draw call数、変換頂点数
が表示されます。render中の行列・色・command・頂点/index bufferは事前確保され、
単調再生はkey cursorを使います。shader/resource/renderの例外は対象sceneだけを一度
無効にし、ほかのskin objectの描画を続行します。

## 非対応範囲

v1は外部animation形式のparserやscript VM、任意GLSL、soft alpha/luma mask、blur、
glow、particleを含みません。変換器は権利を持つ素材から中立documentを生成する別工程
とし、本体へ製品固有データや抽出処理を持ち込みません。
