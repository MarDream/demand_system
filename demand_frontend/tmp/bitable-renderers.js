// src/utils/bitableCellRenderers.ts
import { h } from "vue";
import { VxeUI } from "vxe-table";
var TAG_COLOR_MAP = {
  red: { bg: "rgba(245, 63, 63, 0.12)", fg: "#cb2634", border: "rgba(245, 63, 63, 0.32)" },
  orange: { bg: "rgba(247, 137, 31, 0.12)", fg: "#d46b08", border: "rgba(247, 137, 31, 0.32)" },
  yellow: { bg: "rgba(245, 200, 33, 0.16)", fg: "#a37a07", border: "rgba(245, 200, 33, 0.36)" },
  green: { bg: "rgba(19, 194, 99, 0.12)", fg: "#138a4f", border: "rgba(19, 194, 99, 0.32)" },
  teal: { bg: "rgba(13, 148, 136, 0.12)", fg: "#0c7569", border: "rgba(13, 148, 136, 0.32)" },
  blue: { bg: "rgba(37, 99, 235, 0.12)", fg: "#1d4ed8", border: "rgba(37, 99, 235, 0.32)" },
  purple: { bg: "rgba(99, 102, 241, 0.12)", fg: "#4f46e5", border: "rgba(99, 102, 241, 0.32)" },
  pink: { bg: "rgba(236, 72, 153, 0.12)", fg: "#be185d", border: "rgba(236, 72, 153, 0.32)" },
  gray: { bg: "rgba(107, 114, 128, 0.12)", fg: "#4b5563", border: "rgba(107, 114, 128, 0.32)" },
  default: { bg: "rgba(37, 99, 235, 0.10)", fg: "#2563eb", border: "rgba(37, 99, 235, 0.28)" }
};
function resolveTagColor(color) {
  if (!color) return TAG_COLOR_MAP.default;
  return TAG_COLOR_MAP[color] || TAG_COLOR_MAP.default;
}
function toArrayValue(cellValue) {
  if (cellValue == null || cellValue === "") return [];
  if (Array.isArray(cellValue)) return cellValue.map((v) => String(v));
  const str = String(cellValue);
  if (str.startsWith("[")) {
    try {
      const parsed = JSON.parse(str);
      if (Array.isArray(parsed)) return parsed.map((v) => String(v));
    } catch {
    }
  }
  return str.split(",").map((s) => s.trim()).filter(Boolean);
}
VxeUI.renderer.add("BitableProgress", {
  renderTableDefault(_renderOpts, params) {
    const { row, column } = params;
    const raw = row[column.field];
    let num = Number(raw);
    if (!Number.isFinite(num)) num = 0;
    if (num < 0) num = 0;
    if (num > 100) num = 100;
    const fillColor = num < 30 ? "var(--color-warning, #F59E0B)" : "var(--color-primary, #2563EB)";
    return h("div", { class: "bitable-progress-cell" }, [
      h("div", { class: "bitable-progress-cell__bar" }, [
        h("div", {
          class: "bitable-progress-cell__fill",
          style: { width: `${num}%`, background: fillColor }
        })
      ]),
      h("span", { class: "bitable-progress-cell__text" }, `${num}%`)
    ]);
  }
});
VxeUI.renderer.add("BitableSelectTag", {
  renderTableDefault(renderOpts, params) {
    const { row, column } = params;
    const options = renderOpts.options || [];
    const optionProps = renderOpts.optionProps || { label: "label", value: "label" };
    const labelKey = optionProps.label || "label";
    const valueKey = optionProps.value || "value";
    const isMultiple = !!renderOpts.props?.multiple;
    const raw = row[column.field];
    const values = isMultiple ? toArrayValue(raw) : raw == null || raw === "" ? [] : [String(raw)];
    if (values.length === 0) {
      return h("span", { class: "bitable-cell-empty" }, "");
    }
    return h(
      "div",
      { class: `bitable-tag-cell${isMultiple ? " is-multiple" : ""}` },
      values.map((val) => {
        const opt = options.find((o) => String(o[valueKey]) === val);
        const color = resolveTagColor(opt?.color);
        return h(
          "span",
          {
            class: "bitable-tag-cell__item",
            style: {
              background: color.bg,
              color: color.fg,
              borderColor: color.border
            }
          },
          opt ? String(opt[labelKey]) : val
        );
      })
    );
  }
});
VxeUI.renderer.add("BitableCheckbox", {
  renderTableDefault(_renderOpts, params) {
    const { row, column } = params;
    const raw = row[column.field];
    const checked = raw === true || raw === "true" || raw === "True" || raw === 1 || raw === "1";
    return h("div", { class: "bitable-checkbox-cell" }, [
      h("i", {
        class: checked ? "ri-checkbox-line bitable-checkbox-cell--checked" : "ri-checkbox-blank-line"
      })
    ]);
  }
});
VxeUI.renderer.add("BitableRate", {
  renderTableDefault(renderOpts, params) {
    const { row, column } = params;
    const raw = row[column.field];
    let num = Number(raw);
    if (!Number.isFinite(num)) num = 0;
    const max = Number(renderOpts.props?.max) || 5;
    const stars = [];
    for (let i = 1; i <= max; i++) stars.push(i);
    return h("div", { class: "bitable-rate-cell" }, [
      ...stars.map(
        (i) => h("i", {
          class: i <= num ? "ri-star-fill bitable-rate-cell--active" : "ri-star-line"
        })
      ),
      h("span", { class: "bitable-rate-cell__text" }, String(num))
    ]);
  }
});
var BitableProgressRenderer = "BitableProgress";
var BitableSelectTagRenderer = "BitableSelectTag";
var BitableCheckboxRenderer = "BitableCheckbox";
var BitableRateRenderer = "BitableRate";
export {
  BitableCheckboxRenderer,
  BitableProgressRenderer,
  BitableRateRenderer,
  BitableSelectTagRenderer
};
