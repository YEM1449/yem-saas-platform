export type TemplateType = 'CONTRACT' | 'RESERVATION' | 'CALL_FOR_FUNDS';

export interface TemplateSummary {
  id: string;
  templateType: TemplateType;
  updatedAt: string;
}

export interface TemplateSourceResponse {
  templateType: TemplateType;
  htmlContent: string;
  custom: boolean;
}
