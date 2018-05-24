package com.dasom.ex.user.service;

import com.dasom.ex.user.domain.User;

public interface UserService {
	void add(User user);
	void upgradeLevels();
}
