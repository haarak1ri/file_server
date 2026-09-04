package com.example.file_server;

import com.example.file_server.dto.RegisterRequest;
import com.example.file_server.models.User;
import com.example.file_server.repository.UserRepository;
import com.example.file_server.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Запускаются с профилем  local и тестовой бд.
 * Для использования с H2 добавитв src/test/resources/application-test.properties:
 *   spring.datasource.url=jdbc:h2:mem:testdb
 *   spring.datasource.driver-class-name=org.h2.Driver
 *   spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
 *   fileserver.root-dir=/tmp/filecloud-test
 *   spring.profiles.active=local
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FileServerApplicationTests {

	@Autowired MockMvc mockMvc;
	@Autowired UserRepository userRepository;
	@Autowired UserService userService;
	@Autowired PasswordEncoder passwordEncoder;

	private final ObjectMapper mapper = new ObjectMapper();


	// Вспомогательные методы


	// SHA-256, как на клиенте
	private String sha256(String input) throws Exception {
		java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
		byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		return java.util.HexFormat.of().formatHex(hash);
	}

	// Basic-Auth заголовок: username + SHA-256(password) =
	private String basicAuth(String username, String plainPassword) throws Exception {
		String credentials = username + ":" + sha256(plainPassword);
		return "Basic " + Base64.getEncoder().encodeToString(
				credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private RegisterRequest req(String u, String p) {
		RegisterRequest r = new RegisterRequest();
		r.setUsername(u);
		r.setPassword(p);   // уже SHA-256 (как шлёт клиент)
		return r;
	}


	// 1. Регистрация


	@Test
	@Order(1)
	@DisplayName("Регистрация нового пользователя → 201")
	void register_newUser_returns201() throws Exception {
		String passwordHash = sha256("testPass123");
		String body = mapper.writeValueAsString(req("alice", passwordHash));

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.username").value("alice"));
	}

	@Test
	@Order(2)
	@DisplayName("Регистрация дублирующего логина → 409 Conflict")
	void register_duplicateUser_returns409() throws Exception {
		// Сначала создаём
		String passwordHash = sha256("pass");
		String body = mapper.writeValueAsString(req("bob", passwordHash));
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON).content(body));

		// Повторная попытка
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isConflict());
	}

	@Test
	@Order(3)
	@DisplayName("После регистрации пароль хранится в БД как BCrypt-хэш")
	void register_passwordStoredAsBcrypt() throws Exception {
		String passwordHash = sha256("mySecret");
		String body = mapper.writeValueAsString(req("carol", passwordHash));
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON).content(body));

		User stored = userRepository.findByUsername("carol")
				.orElseThrow(() -> new AssertionError("User not found"));

		// В БД не хранится ни plain-text, ни SHA-256  только BCrypt
		assertThat(stored.getPasswordHash()).startsWith("$2a$");
		assertTrue(passwordEncoder.matches(passwordHash, stored.getPasswordHash()),
				"BCrypt должен подтверждать SHA-256 хэш пароля");
	}

	@Test
	@Order(4)
	@DisplayName("После регистрации создаётся директория пользователя")
	void register_userDirectoryCreated() throws Exception {
		String passwordHash = sha256("dirPass");
		String body = mapper.writeValueAsString(req("dave", passwordHash));
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON).content(body));

		User user = userRepository.findByUsername("dave")
				.orElseThrow(() -> new AssertionError("User not found"));

		assertNotNull(user.getRootFolder(), "rootFolder не должен быть null");
		assertThat(user.getRootFolder()).startsWith("cloud-storage/users/");
	}


	// 2. Аутентификация


	@Test
	@Order(5)
	@DisplayName("Запрос без Authorization → 401")
	void listFiles_noAuth_returns401() throws Exception {
		mockMvc.perform(get("/api/files"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@Order(6)
	@DisplayName("Запрос с неверным паролем → 401")
	void listFiles_wrongPassword_returns401() throws Exception {
		// Сначала регистрируем пользователя
		String correctHash = sha256("correctPass");
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(req("eve", correctHash))));

		// Пробуем с другим паролем
		mockMvc.perform(get("/api/files")
						.header("Authorization", basicAuth("eve", "wrongPass")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@Order(7)
	@DisplayName("Запрос с верными данными → 200")
	void listFiles_correctAuth_returns200() throws Exception {
		String plainPass = "frank_pass";
		String correctHash = sha256(plainPass);
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(req("frank", correctHash))));

		mockMvc.perform(get("/api/files")
						.header("Authorization", basicAuth("frank", plainPass)))
				.andExpect(status().isOk());
	}


	// 3. Файловые операции


	@Test
	@Order(8)
	@DisplayName("Создание папки → 201, папка существует на диске")
	void createFolder_returns201() throws Exception {
		String plainPass = "grace_pass";
		String hash = sha256(plainPass);
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(req("grace", hash))));

		// Уникальное имя для каждого запуска теста
		String folderName = "test_folder_" + System.currentTimeMillis();

		mockMvc.perform(post("/api/files/folders")
						.header("Authorization", basicAuth("grace", plainPass))
						.param("path", folderName))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/files")
						.header("Authorization", basicAuth("grace", plainPass))
						.param("path", ""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == '" + folderName + "' && @.directory == true)]").exists());
	}

	@Test
	@Order(9)
	@DisplayName("Создание дублирующей папки → 4xx ошибка")
	void createFolder_duplicate_returnsError() throws Exception {
		String plainPass = "henry_pass";
		String hash = sha256(plainPass);
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(req("henry", hash))));

		mockMvc.perform(post("/api/files/folders")
				.header("Authorization", basicAuth("henry", plainPass))
				.param("path", "music"));

		// Повтор
		mockMvc.perform(post("/api/files/folders")
						.header("Authorization", basicAuth("henry", plainPass))
						.param("path", "music"))
				.andExpect(result -> assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400));
	}

	@Test
	@Order(10)
	@DisplayName("Path traversal атака → 403 Forbidden")
	void pathTraversal_blocked() throws Exception {
		String plainPass = "ivan_pass";
		String hash = sha256(plainPass);
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(req("ivan", hash))));

		// Пытаемся выйти за пределы home директории
		mockMvc.perform(get("/api/files")
						.header("Authorization", basicAuth("ivan", plainPass))
						.param("path", "../../etc"))
				.andExpect(result ->
						assertThat(result.getResponse().getStatus()).isIn(403, 400, 500));
	}

	@Test
	@Order(11)
	@DisplayName("Список файлов у нового пользователя → пустой массив")
	void listFiles_newUser_returnsEmptyArray() throws Exception {
		String plainPass = "julia_pass";
		String hash = sha256(plainPass);
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(req("julia", hash))));

		mockMvc.perform(get("/api/files")
						.header("Authorization", basicAuth("julia", plainPass))
						.param("path", ""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$").isEmpty());
	}


	// 4. UserService — юнит-тесты


	@Test
	@Order(12)
	@DisplayName("UserService.userExists → false для нового логина")
	void userExists_returnsFalse() {
		assertFalse(userService.userExists("definitely_not_registered_xyz"));
	}

	@Test
	@Order(13)
	@DisplayName("UserService.userExists → true после регистрации")
	void userExists_returnsTrue() throws Exception {
		String hash = sha256("somePass");
		userService.register("known_user", hash);
		assertTrue(userService.userExists("known_user"));
	}


	// Cleanup после всех тестов


	@AfterEach
	void cleanupTestUsers() {
		// Удаляем тестовых пользователей чтобы тесты были идемпотентны
		for (String u : new String[]{"alice","bob","carol","dave","eve","frank",
				"grace","henry","ivan","julia","known_user"}) {
			userRepository.findByUsername(u).ifPresent(userRepository::delete);
		}
	}
}