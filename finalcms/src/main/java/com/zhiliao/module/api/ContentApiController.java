package com.zhiliao.module.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Description:内容api
 *
 * @author Jin
 * @create 2017-05-31
 **/
@RestController
@RequestMapping("/api/")
public class ContentApiController {

    @ResponseBody
    public String getContent(){
        return "";
    }

   @RequestMapping(value="", method= RequestMethod.POST)
    @ResponseBody
    public String postUser() {
        return "success";
    }
}