# Module 16: PDF Generation

## Why This Matters

Formal real-estate workflows need printable, shareable documents.

## Learning Goals

- understand how HTML templates become PDFs
- understand where template editing fits in
- understand the rendering constraints of the chosen stack

## Rendering Pipeline

1. create a model map
2. render HTML through Thymeleaf
3. convert HTML to PDF using OpenHTMLToPDF
4. return or store the PDF in the business flow

## Files To Study

- [../../hlm-backend/src/main/java/com/yem/hlm/backend/deposit/service/pdf/DocumentGenerationService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/deposit/service/pdf/DocumentGenerationService.java)
- [../../hlm-backend/src/main/resources/templates/documents/contract.html](../../hlm-backend/src/main/resources/templates/documents/contract.html)
- [../../hlm-backend/src/main/resources/templates/documents/reservation.html](../../hlm-backend/src/main/resources/templates/documents/reservation.html)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/contract/template/api/TemplateController.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/contract/template/api/TemplateController.java)

## Practical Constraint

PDF-oriented HTML is not the same as browser-optimized HTML.
Keep layouts simple and predictable.

## Exercise

Open one template and list the business values it needs from the model before rendering can succeed.
