# Initial rendering support

Serox currently renders a documented subset of HTML and CSS through JavaFX Canvas.
Unsupported CSS declarations are ignored and reported by the page view.

## CSS

Supported selectors are tag, ID, class, attribute-presence, and attribute-equality
selectors, including descendant combinators and comma-separated groups. The cascade
uses selector specificity, source order, inline declarations, and inherited
`color`, `font-size`, `font-weight`, and `font-family` values.

Supported properties are colors and backgrounds, font size and weight, font family,
text decoration, margin, padding, border, width, height, and `display: block`,
`inline`, `flex`, or `none`. Lengths use pixels. Colors use the named initial palette or
three- and six-digit hexadecimal notation.

### Flexbox

`display: flex` is supported on block containers with these properties:
`flex-direction` (`row`, `column`), `justify-content` (`flex-start`, `center`, `flex-end`,
`space-between`, `space-around`), `align-items` (`stretch`, `flex-start`, `center`,
`flex-end`), `gap`, and `flex-grow` (or the first number of `flex`). Items without a width
take the width of their content; items that do not fit shrink in proportion to their width.
Nested flex containers work.

Not supported in flex layout: `flex-wrap` (items stay on one line), `flex-basis`,
`flex-shrink`, `order`, `align-self`, `align-content`, `inline-flex`, `justify-content` along
a column (it needs a container height), and text placed directly inside a flex container
(only element children become items).

Grid, positioning, pseudo-classes, media queries, transforms, animations,
percentage lengths, and other unsupported values fall back to initial styles.

## Layout and paint

Block elements flow vertically. Inline text uses deterministic character-width
estimates and wraps within the content width. The box model includes margin,
padding, border width, and explicit pixel width and height. Document-order paint
commands define overlap order. The Canvas viewport supports horizontal and vertical
scrolling and recalculates layout after resize.

Text, backgrounds, borders, links, and basic input/button outlines are painted.
Images use a labeled placeholder until resource loading is integrated. Font metrics
are approximate; this renderer is an initial independent engine, not a full browser
layout implementation.
