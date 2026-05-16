# MyPlans Auth Service

Microservicio de autenticación e IAM para la plataforma MyPlans.
Gestiona usuarios, roles, permisos modulares y emite JWT para el resto
de los microservicios. Cubre las épicas de Gestión de Usuarios y
Autenticación del documento de diseño.

## Cómo correr

```bash
cd auth
mvn clean package -DskipTests
java -jar -Dspring.profiles.active=dev target/auth-0.0.1-SNAPSHOT.jar
```

Profiles:
- `dev` (default): MySQL local en `localhost:3306/db_myplans_auth`.

El servicio queda escuchando en `http://localhost:8090`.

## Cambios aplicados sobre el código original

### 1. JWT con claims para integración con Core

`JwtUtil` ahora incluye dos claims adicionales en el payload del token:

- **`id_usuario`** — id numérico del usuario en BD. El Core lo usa
  para registrar `idUsuarioIngreso` e `idUsuarioActualizacion` en los
  TAGs (FK lógica hacia el Auth, según el modelo ER).
- **`roles`** — lista con prefijo `ROLE_` (ej. `["ROLE_ADMIN"]`).
  El Core lo usa para el RBAC (CU-13 reversa solo Supervisor, CU-16
  validar solo Supervisor, etc.).

Sin estos claims, el Core tendría que llamar HTTP al Auth en cada
request para resolver el rol, agregando latencia innecesaria.

**Compatibilidad:** la firma sigue siendo HS384 (BASE64 decode del
mismo secret), el subject sigue siendo el email, la expiración sigue
siendo `${jwt.expiration}` ms. Los tokens previos siguen siendo
válidos. El frontend no necesita cambios.

### 2. Manejo centralizado de errores

Antes, cada controller tenía `try/catch` envolviendo cada llamada a
service. Los services lanzaban `RuntimeException` con mensaje, los
controllers lo capturaban y devolvían un `Map` con campos distintos
según el endpoint — formatos inconsistentes y stack traces filtrados
en errores 5xx.

Ahora:

- **`GlobalExceptionHandler`** centraliza todo en un solo lugar.
- **Controllers limpios** sin try/catch.
- **Body JSON uniforme** para todos los errores 4xx/5xx:

  ```json
  {
    "timestamp": "2026-05-11T22:47:45.471",
    "status": 400,
    "error": "Bad Request",
    "message": "Mensaje legible para el usuario"
  }
  ```

- **Fallback** que loguea stack trace internamente pero devuelve sólo
  `"Ocurrió un error inesperado. Por favor intenta más tarde"` al
  frontend.

### 3. Excepciones tipadas

Reemplazan el uso de `RuntimeException` genérico en services:

| Excepción | HTTP | Uso |
| --------- | ---- | --- |
| `ResourceNotFoundException` | 404 | Usuario/rol/módulo no existe |
| `EmailAlreadyExistsException` | 409 | Correo duplicado en registro |
| `RutAlreadyExistsException` | 409 | RUT duplicado en registro |
| `InvalidPasswordException` | 400 | Contraseña inválida (actual incorrecta, no cumple política, etc.) |
| `NoFieldsToUpdateException` | 400 | Body vacío en PUT |
| `BusinessException` | 400 | Regla de negocio genérica |

### 4. Política de contraseñas (`PasswordPolicy`)

Validador centralizado con 5 reglas, cada una con su propio mensaje
específico para que el frontend pueda indicar al usuario qué falla:

- Mínimo 8 caracteres
- Al menos una letra mayúscula
- Al menos una letra minúscula
- Al menos un número
- Al menos un carácter especial (`!@#$%^&*…`)

Se aplica en `/api/auth/register`, `/api/auth/new-password` (reset),
`PUT /api/auth/me/password` (cambio desde perfil) y
`POST /api/admin/users` (creación por admin).

Además, al cambiar contraseña desde el perfil, se valida que la nueva
**no sea igual** a la actual.

### 5. Endpoint de logout

`POST /api/auth/logout` — público, siempre responde 200 incluso si el
token está vencido o ausente. Limpia el `SecurityContext` del servidor;
el frontend descarta el token de localStorage y redirige al login.

Esto permite implementar el logout automático en el frontend con un
interceptor que escuche cualquier 401:

```js
axios.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem("token");
      window.location.href = "/login";
    }
    return Promise.reject(error);
  }
);
```

### 6. Manejo específico de fallos de JWT

`JwtAuthFilter` distingue causas y guarda mensajes precisos en el
request para que el `CustomAuthenticationEntryPoint` los use:

- `ExpiredJwtException` → "Tu sesión ha expirado. Por favor inicia sesión nuevamente"
- `SignatureException` / `MalformedJwtException` / `UnsupportedJwtException` → "Token inválido. Por favor inicia sesión nuevamente"
- Cuenta deshabilitada → "Tu cuenta está deshabilitada. Contacta a un administrador"
- Sin token → "Debes iniciar sesión para acceder a este recurso"

### 7. EntryPoint y AccessDeniedHandler personalizados

`CustomAuthenticationEntryPoint` y `CustomAccessDeniedHandler`
responden 401/403 con el mismo formato JSON consistente del
`GlobalExceptionHandler`, en vez de los 403 vacíos genéricos que
Spring Security daba por defecto.

### 8. Validación en DTOs

- **`UserRegisterDTO`**: `@NotBlank` en `nombreCompleto` (la entidad
  tiene NOT NULL en BD; sin esta validación, faltar el campo causaba
  500 por `DataIntegrityViolationException`).
- **`ChangePasswordDTO`**: `@NotBlank` por campo con mensajes
  específicos ("Debes ingresar tu contraseña actual" / "Debes ingresar
  la nueva contraseña") para que el frontend muestre exactamente qué
  falta.
- **`AdminUpdateDTO`**: método `isEmpty()` que detecta cuando ningún
  campo viene con valor. El `PUT /api/admin/users/{id}` lanza
  `NoFieldsToUpdateException` con un mensaje listando los campos
  editables.

### 9. Otros arreglos

- **`pom.xml`**: agregado `<parameters>true</parameters>` al
  `maven-compiler-plugin`. Spring 6+ lo requiere para que
  `@PathVariable Long id` resuelva el nombre del parámetro sin
  declararlo manualmente. Sin esto, los endpoints con path variables
  fallaban en runtime con 500.

## Estructura de la API

Todas las respuestas de error siguen el formato JSON uniforme
descrito arriba. Las respuestas exitosas (2xx) usan
`{"message": "..."}` o el DTO específico del recurso.

### Endpoints públicos (`/api/auth/**`)

| Verbo | Path | Descripción |
| ----- | ---- | ----------- |
| POST | `/api/auth/register` | Registro de usuario (validación de email/RUT duplicados + política de contraseña) |
| POST | `/api/auth/login` | Iniciar sesión, devuelve JWT |
| POST | `/api/auth/logout` | Cerrar sesión (siempre 200) |
| POST | `/api/auth/reset-password` | Solicitar recuperación (siempre 200 por seguridad) |
| POST | `/api/auth/new-password` | Establecer nueva contraseña con token de recuperación |

### Endpoints autenticados (`/api/auth/me*`)

| Verbo | Path | Descripción |
| ----- | ---- | ----------- |
| GET | `/api/auth/me` | Perfil del usuario autenticado |
| PUT | `/api/auth/me` | Actualizar perfil propio (nombre, teléfono) |
| PUT | `/api/auth/me/password` | Cambiar contraseña propia |

### Endpoints de admin (`/api/admin/**`) — requieren `ROLE_ADMIN`

| Verbo | Path | Descripción |
| ----- | ---- | ----------- |
| GET | `/api/admin/users` | Listar usuarios |
| POST | `/api/admin/users` | Crear usuario (aprovisionamiento directo) |
| PUT | `/api/admin/users/{id}` | Editar usuario (mensaje claro si body vacío) |
| PATCH | `/api/admin/users/{id}/toggle-status` | Activar/desactivar |
| POST | `/api/admin/users/{userId}/roles/{roleName}` | Asignar rol |
| DELETE | `/api/admin/users/{userId}/roles/{roleName}` | Revocar rol |
| GET | `/api/admin/roles` | Listar roles |
| POST | `/api/admin/roles` | Crear rol |
| GET | `/api/admin/modules` | Listar módulos |
| GET | `/api/admin/access-types` | Listar tipos de acceso |
| POST | `/api/admin/roles/{idRol}/permissions` | Otorgar permiso a rol |
| DELETE | `/api/admin/roles/{idRol}/permissions` | Revocar permiso de rol |

### Swagger

Disponible en `http://localhost:8090/swagger-ui.html`.

## Integración con el Core

El JWT emitido por el Auth contiene los claims `id_usuario` (Integer)
y `roles` (lista con prefijo `ROLE_`). El microservicio Core los lee
para:

1. Identificar al usuario que realiza cada operación (idUsuarioIngreso
   e idUsuarioActualizacion en los TAGs).
2. Hacer RBAC fino con `@PreAuthorize("hasRole('SUPERVISOR')")`.

**Importante**: ambos servicios deben compartir el mismo `jwt.secret`
en sus `application.yml` o, mejor, vía variable de entorno
`JWT_SECRET`. La firma usa HS384 (`Decoders.BASE64.decode(secret)`).

## Próximos pasos sugeridos

1. **Refresh tokens**: actualmente el JWT expira a las 24 horas sin
   posibilidad de refrescar. Agregar refresh tokens permitiría
   sesiones más largas sin sacrificar seguridad.
2. **Email real para reset**: actualmente `EmailService` registra el
   correo en logs. Integrar con SMTP o un servicio como SES.
3. **Tests con MySQL real**: los tests usan el profile `dev` apuntando
   a MySQL. Para CI/CD conviene agregar Testcontainers o un profile
   `h2test` como hicimos en el Core.
4. **Eventos para auditoría**: cuando exista el microservicio Audit,
   publicar eventos en login, cambio de contraseña, cambio de rol, etc.
