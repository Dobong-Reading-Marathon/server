package com.dobong.dobong.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	/** phone은 PhoneNumbers로 정리한 값을 넣음. */
	boolean existsByPhone(String phone);
}
