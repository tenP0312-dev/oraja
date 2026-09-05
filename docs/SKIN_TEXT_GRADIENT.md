# Skin text vertical gradients

JSON and Lua skins can give dynamic TrueType/OpenType text a vertical color
gradient. Add both optional colors to a `text` entry:

```lua
{
  id = "accent-title",
  font = "display-font",
  size = 64,
  ref = 12,
  align = 1,
  gradientTopColor = "fff4cfff",
  gradientBottomColor = "ff6f3cff",
}
```

Colors use the existing `RRGGBBAA` notation. The top and bottom values are
interpolated from the destination rectangle's upper and lower edges. The
active destination color and alpha multiply the gradient, so ordinary color
and fade animation continue to work.

Both fields are required to enable the effect. If either field is absent, the
text keeps the legacy single-color rendering path. Existing skins therefore
remain compatible without changes.
