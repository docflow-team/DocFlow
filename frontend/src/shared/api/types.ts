// DTOs from contracts/openapi.yaml; dates use ISO 8601 strings.
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: Record<string, string>;
}

export interface UserResponse {
  id: number;
  username: string;
  email: string;
  role: 'USER' | 'ADMIN';
  enabled: boolean;
  mustChangePassword: boolean;
}

export interface LoginRequest {
  login: string;
  password: string;
}
export interface PasswordChangeRequest {
  currentPassword: string;
  newPassword: string;
}
export interface TokenResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: UserResponse;
}

export interface CategoryResponse {
  id: number;
  name: string;
  description: string | null;
}
export interface MetadataRequest {
  title: string;
  description: string | null;
}
export interface CategoriesRequest {
  categoryIds: number[];
}
export interface TagsRequest {
  tags: string[];
}
export interface DocumentResponse {
  id: number;
  title: string;
  description: string | null;
  originalFilename: string;
  contentType: 'application/pdf';
  sizeBytes: number;
  createdAt: string;
  updatedAt: string;
  categories: CategoryResponse[];
  tags: string[];
}

export interface DocumentPageResponse {
  items: DocumentResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProcessingResponse {
  documentId: number;
  processingAvailable: false;
  ocr: 'NOT_STARTED';
  indexing: 'NOT_STARTED';
  summary: 'NOT_STARTED';
  classification: 'NOT_STARTED';
}

export interface UploadResponse {
  document: DocumentResponse;
  processingUrl: string;
  processing: ProcessingResponse;
}
