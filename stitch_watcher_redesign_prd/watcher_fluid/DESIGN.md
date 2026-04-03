# Design System Specification

## 1. Overview & Creative North Star: "The Digital Curator"
This design system moves away from the cold, industrial aesthetic typically associated with security and surveillance. Instead, it adopts the **Digital Curator** ethos: a visual language that feels like a high-end gallery—clean, airy, and hyper-organized, yet deeply approachable. 

The system rejects the "dashboard" look in favor of an editorial layout. We achieve this through **Intentional Asymmetry** (using varied card widths and off-center alignments) and **Tonal Depth** (layering soft surfaces instead of drawing lines). The goal is a "Playful and Professional" interface that feels more like a lifestyle app than a monitoring tool.

---

## 2. Color & Surface Philosophy
The palette is rooted in high-chroma accents set against a sophisticated, multi-tonal neutral base.

### The "No-Line" Rule
**Strict Mandate:** Designers are prohibited from using 1px solid borders to section off content. Structural boundaries must be defined exclusively through background color shifts or subtle tonal transitions. For example, a `surface-container-low` card sits on a `surface` background to create a "soft edge."

### Surface Hierarchy & Nesting
Treat the UI as a physical stack of fine paper or frosted glass.
- **Base Layer:** `surface` (#f7f9fb)
- **Secondary Sectioning:** `surface-container-low` (#f2f4f6)
- **Actionable Containers:** `surface-container-lowest` (#ffffff) for maximum "pop."
- **Nesting:** Use `surface-container-high` (#e6e8ea) for inset elements like search bars or inactive camera feeds to create a recessed, tactile feel.

### The "Glass & Gradient" Rule
To elevate the "Playful" requirement, use **Glassmorphism** for floating navigation bars or overlays. Use `surface-container-lowest` at 70% opacity with a `24px` backdrop blur. 
**Signature Textures:** For primary CTAs, apply a subtle linear gradient from `primary` (#0058be) to `primary-container` (#2170e4) at a 135° angle to provide depth that flat hex codes lack.

---

## 3. Typography: Editorial Clarity
We pair the structural precision of **Inter** with the expressive, wide apertures of **Manrope** to create an authoritative yet friendly voice.

- **Display & Headlines (Manrope):** Used for "The Big Picture"—camera names, AI event summaries, and high-level stats. The generous tracking and rounded shapes of Manrope soften the "technical" nature of AI analysis.
- **Body & Labels (Inter):** Used for high-readability data, time-stamps, and system logs. Inter provides the "Professional" anchor to the "Playful" display type.

**Hierarchy Note:** Always maintain a significant scale jump between `headline-lg` (2rem) and `body-md` (0.875rem) to create the "white space" luxury feel characteristic of high-end editorial design.

---

## 4. Elevation & Depth
Hierarchy is achieved through **Tonal Layering** rather than structural shadows.

- **The Layering Principle:** Place a `surface-container-lowest` card on a `surface-container-low` background. This creates a natural "lift" that feels modern and lightweight.
- **Ambient Shadows:** For high-priority floating elements (e.g., an active AI alert), use an extra-diffused shadow: `X: 0, Y: 12, Blur: 32, Spread: -4`. The color must be a 6% opacity tint of `on-surface` (#191c1e), never pure black.
- **The "Ghost Border" Fallback:** If a boundary is legally or accessibility-required, use the `outline-variant` (#c2c6d6) at **15% opacity**. 100% opaque borders are strictly forbidden as they "trap" the eye.

---

## 5. Components & Layout Patterns

### Cards (The Core Primitive)
- **Style:** Minimum radius of `lg` (2rem/32px).
- **Construction:** Forbid the use of divider lines. Separate "Camera Name" from "Status" using vertical white space (`spacing-4`). 
- **AI Highlight Cards:** Use a `surface-container-lowest` base with a `secondary-fixed` (#6ffbbe) subtle glow on the bottom edge to indicate an active "Success/Online" status.

### Buttons & Chips
- **Primary Button:** Gradient-filled (Primary to Primary-Container), `full` (9999px) radius, using `title-sm` (Inter) for the label.
- **Status Chips:** Use `secondary-container` (#6cf8bb) for "Online" and `error-container` (#ffdad6) for "Alerts." These should feel like soft "pills" floating in the header.

### Stepper Indicators (AI Event Timeline)
- **Style:** Do not use a vertical line connecting dots. Instead, use a series of staggered `surface-container-highest` capsules. The "Current" step uses a `primary` pulse animation.

### Intuitive Status Badges
- **The "Pulse":** For live camera feeds, use a 4px `secondary` (#006c49) dot with a 50% opacity concentric ring that pulses slowly. This is "Friendly" rather than the aggressive "REC" red.

---

## 6. Do’s and Don'ts

### Do:
- **Use Intentional Asymmetry:** If you have three camera feeds, consider making the primary feed 2/3 width and the secondary feeds stacked on the right to break the "grid" monotony.
- **Embrace the "xl" Radius:** Use `xl` (3rem) corner radii for large layout containers to emphasize the "friendly" aesthetic.
- **Color-Coded Analysis:** Use `tertiary` (#005da8) for AI motion paths—it's sophisticated and distinct from "Action Blue."

### Don’t:
- **No Dark Mode Industrialism:** Avoid pitch-black backgrounds. Even in dark themes, use deep navy-grays to maintain the "Friendly" brand promise.
- **No High-Contrast Borders:** Never use a border that has more than a 1:1.2 contrast ratio with its background.
- **No Tight Spacing:** If you think a section has enough padding, add `spacing-4` more. The "Watcher" brand lives in the breathing room.

---

## 7. Token Reference Summary

| Token Name | Value | Usage |
| :--- | :--- | :--- |
| `primary` | #0058be | Vital actions, high-importance triggers. |
| `secondary` | #006c49 | Online status, "All Clear" AI confirmations. |
| `surface` | #f7f9fb | Main application canvas. |
| `radius-lg` | 2rem (32px) | Standard card containers. |
| `radius-xl` | 3rem (48px) | Large hero sections / screen wrappers. |
| `spacing-6` | 2rem (32px) | Standard gutter between major components. |