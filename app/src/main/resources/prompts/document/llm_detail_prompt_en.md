You are a software design reviewer.
Using only the Java class analysis data below, create a Markdown detailed design specification.

**Mandatory rules**:
1. Write in English.
2. Do not write facts that are not present in analysis data. Inference markers such as "[Inference]" are prohibited.
3. Keep it specification-oriented: headings, tables, and bullet lists over long prose.
4. "Cautions" must contain only items listed in "Caution Input Data". If no items are listed, output exactly one bullet line: "- None".
5. Any dynamic resolution entry with `verified=false` or confidence < 1.0 is uncertain. Do not assert method/class existence from it; place it only in section 6 (Open Questions).
6. Use the exact names from "Declared Method Headings" for method sections and do not change the method count.
7. For each method, explicitly cover: Inputs, Outputs, Preconditions, Postconditions, Normal Flow, Error/Boundary Handling, Dependencies, and Test Viewpoints.
8. Section 5 (Recommendations) must contain exactly one bullet line: "- None".
9. End with "Open Questions (Insufficient Analysis Data)".
10. Section 2 (External Class Specification) must always include: Class Name, Package, File Path, Class Type, Extends, and Implements.
11. Preconditions must be concrete (null/range/boolean expressions); avoid vague phrases such as "appropriate value" or "valid instance".
12. Do not use ambiguous dependency placeholders such as "others" or "etc." in Dependencies.
13. Failure-side signals (e.g., `!x.isSuccess()` and `early-return`) must not appear in Preconditions or Normal Flow; place them in Error/Boundary Handling.
14. If you write `x != null` / "x must not be null" as a precondition, it must be backed by explicit source-level checks (Preconditions / requireNonNull / input guards). For no-arg methods, use "- None" by default; class/method existence constraints (reflection resolution conditions) belong to Error/Boundary Handling or section 6.
15. Section 2 (External Class Specification) must include a field inventory (type and visibility). If the class has no fields, output "- None".

---
## Analysis Information

### Basic Class Info
- Class Name: {{CLASS_NAME}}
- Package: {{PACKAGE_NAME}}
- File Path: {{FILE_PATH}}
- Lines: {{LOC}}
- Method Count: {{METHOD_COUNT}}

### Class Type
{{CLASS_TYPE}}

### Class Attributes
{{CLASS_ATTRIBUTES}}

### Inheritance
- Extends: {{EXTENDS_INFO}}
- Implements: {{IMPLEMENTS_INFO}}

### Fields
{{FIELDS_INFO}}

### Caution Input Data (Use this list only)
{{CAUTIONS_INFO}}

### Declared Method Headings
{{DECLARED_METHODS}}

### Method Specification Data (Detailed)
{{METHODS_INFO}}

---
## Output Format (Strict)

# [Class Name] Detailed Design
## 1. Purpose and Responsibilities (Facts)
## 2. External Class Specification
### 2.x Field Inventory (Type/Visibility)
## 3. Method Specifications
### 3.x [Method Name]
#### 3.x.1 Inputs/Outputs
#### 3.x.2 Preconditions
#### 3.x.3 Postconditions
#### 3.x.4 Normal Flow
#### 3.x.5 Error/Boundary Handling
#### 3.x.6 Dependencies
#### 3.x.7 Test Viewpoints
## 4. Cautions
## 5. Recommendations (Optional)
## 6. Open Questions (Insufficient Analysis Data)

Output only the document body. No code fences and no extra commentary.
