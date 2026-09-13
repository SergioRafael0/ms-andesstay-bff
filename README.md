# 🛡️ AndesStay — BFF (Backend for Frontend)

Caso 5 EP1 — DSY1107 Cloud Native I.

Servicio **Spring Boot 3** que actúa como **BFF** (Backend for Frontend). Su responsabilidad:

1. ✅ Recibe las llamadas que llegan desde **AWS API Gateway**.
2. ✅ **Valida el JWT** emitido por Microsoft Entra ID (segunda validación, después de API Gateway).
3. ✅ Verifica que el **issuer**, **audience**, **firma** y **vigencia** (exp) del token sean correctos.
4. ✅ Lee los **roles** desde el claim `roles` del JWT.
5. ✅ Aplica **autorización por rol** cuando corresponde.
6. ✅ **Hace de proxy** hacia los microservicios internos (`ms-andesstay-reservations` y `ms-andesstay-catalog`).
7. ❌ **NO se conecta a la base de datos** (regla arquitectónica de la EP1).

## 📡 Endpoints

| Método | Path                       | Acción                                              |
|--------|----------------------------|-----------------------------------------------------|
| ANY    | `/api/reservations/**`     | Proxy → `ms-andesstay-reservations:8081`            |
| ANY    | `/api/catalog/**`          | Proxy → `ms-andesstay-catalog:8082`                 |
| GET    | `/actuator/health`         | Health check (público)                              |

## ⚙️ Configuración

Variables de entorno (o edita `application.yml`):

```bash
ENTRA_ISSUER_URI=https://login.microsoftonline.com/<TU_TENANT_ID>/v2.0
ENTRA_JWK_SET_URI=https://login.microsoftonline.com/<TU_TENANT_ID>/discovery/v2.0/keys
MS_RESERVATIONS_URL=http://localhost:8081/api/reservations
MS_CATALOG_URL=http://localhost:8082/api/catalog
```

## 🔐 Validación del JWT

Spring Security 6 + OAuth2 Resource Server se encargan automáticamente:

- **Issuer** (`iss`): debe ser `https://login.microsoftonline.com/<TENANT>/v2.0`
- **Audience** (`aud`): debe incluir el `clientId` de la App Registration `andesstay-api` (configurado en Entra ID como App ID URI)
- **Firma**: validada contra las JWK públicas de Entra ID
- **Vigencia** (`exp`, `nbf`): validadas automáticamente
- **Roles**: extraídos del claim `roles` (configurado en Entra ID como App Roles)

## 🚀 Comandos

```bash
mvn clean package -DskipTests
java -jar target/ms-andesstay-bff.jar
```

## 📁 Estructura

```
src/main/java/cl/andesstay/bff/
├── BffApplication.java
├── config/
│   ├── SecurityConfig.java     # Filtro de seguridad + JWT
│   └── CorsConfig.java         # CORS
├── controller/
│   ├── ReservationsProxyController.java
│   └── CatalogProxyController.java
└── service/
    └── ProxyService.java       # Reenvía a microservicios
```

## 🔗 Integración con el resto

```
Angular (Bearer Token)
  → AWS API Gateway (valida JWT, authorizer)
    → BFF (este servicio, segunda validación + autorización)
      → ms-andesstay-reservations (puerto 8081)
      → ms-andesstay-catalog (puerto 8082)
```
