# Fruits Theme

**Work in Progress**

This color theme is a modified version of VSCode's default dark theme, inspired by Visual Assist's dark theme and the omnipresent Monokai theme.

It builds upon the following principles:

- Highlight control-flow keywords (red)
- Highlight type and function names as they carry semantics (orange, yellow)
- Remove emphasis from comments, strings, and namespaces
- Keywords and operators serve as *anchor* points (blue)
- Only source code is themed

## Considerations

- Using [Bracket Pair Colorizer](https://marketplace.visualstudio.com/items?itemName=CoenraadS.bracket-pair-colorizer)
- Changing variable coloring to adjust the overall color tone:
  ```
  "editor.tokenColorCustomizations": {
      "variables": "#BDB76B",  // default Visual Assist
      "variables": "#82c0e2",  // default VSCode dark theme
  }
  ```
