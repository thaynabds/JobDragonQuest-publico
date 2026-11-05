# JobFly

> Idioma: [Português](README.md) | [English](README.en.md) | **Español**

Plataforma para gestionar la carrera y automatizar postulaciones a empleos y prácticas, en Brasil y en el exterior. JobFly reúne en un solo lugar todo lo que pasa en cada proceso de selección, venga de donde venga la postulación: LinkedIn, Gupy, el sitio de la empresa, Instagram, un grupo de Facebook, el correo o una recomendación.

**Autora:** [Thayná Batista da Silva](https://github.com/thaynabds) · **Estado:** en desarrollo, incremento 1 (núcleo de la API).

## Qué hace la solución

### Ya funciona (incremento 1, probado)

- **Cuentas seguras.** Registro e inicio de sesión con contraseña protegida por Argon2id, sesión de 15 minutos renovada por un token rotativo que se revoca solo si lo roban, bloqueo tras intentos fallidos y límite de solicitudes.
- **Varios perfiles profesionales.** Por ejemplo: "Desarrolladora Java", "Prácticas de desarrollo", "Vacantes internacionales". Cada perfil tiene objetivo, cargos deseados, competencias con nivel y años de uso, idiomas según el MCER, experiencias, formación, proyectos, cursos, certificaciones, pretensión salarial, modalidades, ciudades, disponibilidad y restricciones. Los perfiles nunca se mezclan.
- **Registro de vacantes con deduplicación.** La misma vacante llega por URLs distintas (con rastreo, otro subdominio, otra ruta de LinkedIn) y JobFly reconoce que es una sola. También reconoce la misma empresa y cargo, sin importar "Ltda.", "S.A." ni acentos.
- **Análisis de compatibilidad explicable.**
  - Puntuación de 0 a 100 con el motivo de cada requisito: cumplido, parcial, no cumplido o "necesita lectura humana".
  - Los impedimentos eliminatorios (modalidad, ciudad, idioma exigido, seniority) van aparte: una nota alta nunca oculta un impedimento.
  - El sistema elige solo el perfil más adecuado para cada vacante.
- **Central unificada de postulaciones.**
  - Registra postulaciones hechas en cualquier lugar e impide duplicados por URL, por empresa y cargo, y por clave de idempotencia.
  - Sigue 19 etapas: de la vacante encontrada a aprobada, rechazada o cerrada, pasando por filtro, entrevistas, pruebas técnicas y oferta.
- **Actualización por evidencia.**
  - Cada cambio de etapa registra el origen (tú, correo, portal, agente), el texto que lo justifica y la confianza.
  - Una confirmación automática nunca se convierte en aprobación, y el silencio nunca en rechazo.
  - La información dudosa se convierte en una sugerencia que aceptas o descartas.
  - Las fuentes automáticas no reabren un proceso cerrado. Solo tú puedes.
- **Panel (API).** Kanban por etapa, resumen con contadores, filtros por perfil, empresa, país, plataforma y período, historial completo de cada postulación y central de pendientes (acción tuya, prueba técnica, entrevista próxima, plazo, sugerencia por revisar, proceso sin novedades).
- **Auditoría** de todas las operaciones relevantes, sin guardar contenido sensible.

### Próximos incrementos

El plan detallado y el estado de cada criterio de aceptación están en [docs/07-roadmap.md](docs/07-roadmap.md) (en portugués). En resumen:

1. Panel web en React (portugués e inglés).
2. Búsqueda automática de vacantes mediante las APIs públicas de Greenhouse, Lever, SmartRecruiters y Ashby, y lista de Empresas Monitoreadas.
3. Gmail y Outlook por OAuth con permisos mínimos: clasificación de mensajes, vínculo automático con la postulación correcta, etiquetas y respuestas autorizadas. Nunca borra correos.
4. Agentes de IA (Claude, OpenAI o modelo local): análisis de preguntas eliminatorias, revisión de la postulación antes del envío y documentos personalizados que usan solo datos reales del perfil.
5. Servidor MCP para usar las herramientas de JobFly desde Claude Desktop y Claude Code.
6. Extensión de navegador "Enviar a JobFly".
7. Currículums versionados, adjuntos e importación de historial (CSV).

## Principios

- **Veracidad.** La IA nunca inventa experiencia, certificados, idiomas ni competencias.
- **Reglas de las plataformas.** Solo APIs oficiales y caminos permitidos. CAPTCHA, MFA y bloqueos nunca se eluden: JobFly avisa qué depende de ti.
- **Tú decides.** Aceptar una oferta, firmar un contrato, asumir un compromiso financiero o compartir datos sensibles siempre requiere tu aprobación.
- **Privacidad (LGPD/RGPD).** Datos separados por usuario, secretos fuera del código, borrado en cascada.

## Arquitectura

Monolito modular en Java 21 y Spring Boot 4.1, con PostgreSQL y Flyway. Los módulos están separados por responsabilidad (`identity`, `profiles`, `jobs`, `matching`, `applications`, `dashboard`, `audit`) y preparados para convertirse en servicios propios cuando haya motivo. Las decisiones y su justificación están en [docs/02-arquitetura.md](docs/02-arquitetura.md) (en portugués).

```
JobFly/
├── jobfly-api/              API REST (Spring Boot)
├── docs/                    requisitos, arquitectura, datos, integraciones, API, seguridad, roadmap
├── auditoria-jobcopilot/    estudio de referencia y matriz de funcionalidades
└── scripts/                 configuración y ejecución local
```

## Cómo ejecutarlo

Requisitos: Java 21 y PostgreSQL 14 o más reciente. Maven viene incluido (`mvnw`).

```bash
# 1. Crea la base "jobfly" y escribe .env con contraseña y secreto JWT aleatorios (pide sudo una vez)
scripts/configurar-local.sh

# 2. Levanta la API en http://localhost:8080
scripts/iniciar-api.sh

# 3. Pruebas, con informe de cobertura en jobfly-api/target/site/jacoco
cd jobfly-api && ./mvnw verify
```

Las variables de entorno están en [.env.example](.env.example). La referencia de la API está en [docs/05-api.md](docs/05-api.md).

## Calidad y seguridad

- 81 pruebas automatizadas (unitarias y de integración con la aplicación completa).
- El build falla si la cobertura de líneas de los módulos críticos baja del 80% (hoy supera el 92%).
- Los controles y pruebas de seguridad están en [docs/06-seguranca.md](docs/06-seguranca.md): aislamiento por usuario, CSRF, XSS, inyección, fuerza bruta, robo de tokens y tamaño de solicitud.

## Licencia

Todos los derechos reservados. Repositorio privado.
