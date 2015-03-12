package com.eowise.packer
import com.eowise.imagemagick.tasks.Magick
import com.eowise.packer.extension.Atlas
import com.eowise.packer.extension.Atlases
import com.eowise.packer.extension.NamedAtlas
import com.eowise.packer.extension.Resolution
import com.eowise.packer.extension.Resolutions
import com.eowise.packer.hooks.Hook
import com.eowise.packer.hooks.Hooks
import org.gradle.api.DefaultTask
import org.gradle.api.Named
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Input
/**
 * Created by aurel on 04/01/14.
 */
class Packer extends DefaultTask {

    @Input
    private Resolution uniqueResolution
    @Input
    private Resolutions resolutions
    @Input
    private Atlas uniqueAtlas
    @Input
    private Atlases atlases
    @Input
    private Hooks beforeResize
    @Input
    private Hooks afterResize

    Closure resourcesPathClosure
    Closure atlasesPathClosure

    
    Packer() {
        this.uniqueResolution = extensions.create('resolution', Resolution)
        this.uniqueAtlas = extensions.create('atlas', Atlas)
        this.resolutions = extensions.create('resolutions', Resolutions)
        this.atlases = extensions.create('atlases', Atlases, project)
        this.beforeResize = extensions.create('beforeResize', Hooks)
        this.afterResize = extensions.create('afterResize', Hooks)
        
        project.configure(project) {
            afterEvaluate {
                this.setup()
            }
        }
    }

    def defaultResolutions() {
        resolutions.add('ldpi', 0.375f)
        resolutions.add('mdpi', 0.5f)
        resolutions.add('hdpi', 0.75f)
        resolutions.add('xhdpi')
    }
    
    def resourcesInputPath(String resourcesPath) {
        this.resourcesPathClosure = { Atlas atlas -> atlas instanceof Named ? "${resourcesPath}/${atlas}" : resourcesPath }
    }

    def resourcesInputPath(Closure closure) {
        this.resourcesPathClosure = closure
    }


    def atlasesOutputPath(Closure closure) {
        this.atlasesPathClosure = closure
    }

    def atlasesOutputPath(String atlasesPath) {
        this.atlasesPathClosure = { Resolution resolution -> "${atlasesPath}/${resolution}" }
    }

    String resourcesPath(Atlas atlas) {
        return resourcesPathClosure(atlas)
    }

    String atlasesPath(Resolution res) {
        return atlasesPathClosure(res)
    }

    def setup() {

        if (atlases.size() == 0)
            atlases.add(uniqueAtlas)
        
        if (resolutions.size() == 0)
            resolutions.add(uniqueResolution)
        
        atlases.each() {
            atlas ->

                Task convertSvg = project.tasks.create(name: "${name}ConvertSvg${atlas}", type: Magick) {
                    convert resourcesPath(atlas), atlas.svgs
                    into resourcesPath(atlas)
                    actions {
                        inputFile()
                        outputFile { fileName, extension -> "${fileName}.png" }
                    }
                }

                dependsOn.each { d -> convertSvg.mustRunAfter d }

                resolutions.each() {
                    resolution ->

                        Task resizeImagesTask = project.tasks.create(name: "${name}ResizeImages${resolution}${atlas}", type: Magick, dependsOn: "${name}ConvertSvg${atlas}")

                        dependsOn.each { d -> resizeImagesTask.mustRunAfter d }

                        /*
                        beforeResize.each {
                            Hook hook ->
                                // if applyToAtlases is not set, we apply the hook to all atlases
                                if (hook.applyToAtlases.contains(atlas.toString()) || hook.applyToAtlases.size() == 0) {

                                    project.configure(resizeImagesTask, hook.task)
                                }
                        }
                        */

                        project.configure(resizeImagesTask) {
                            convert resourcesPath(atlas), atlas.textures
                            into "out/resources/${resolution}/${atlas}"
                            actions {
                                inputFile()
                                -resize(resolution.ratio * 100 + '%')
                                outputFile()
                            }


                        }


                        project.tasks.create(name: "${name}CopyPacks${resolution}${atlas}", type: Copy) {
                            from resourcesPath(atlas)
                            into "out/resources/${resolution}/${atlas}"
                            include "${resolution}.json"
                            rename { f -> 'pack.json' }
                        }

                        def createPacks = project.tasks.create(name: "${name}CreatePacks${resolution}${atlas}", type: TexturePacker, dependsOn: ["${name}ResizeImages${resolution}${atlas}", "${name}CopyPacks${resolution}${atlas}"]) {
                            from atlas.toString(), "out/resources/${resolution}"
                            into atlasesPath(resolution)
                        }

                        afterResize.each {
                            Hook hook ->
                                // if applyToAtlases is not set, we apply the hook to all atlases
                                if (hook.applyToAtlases.contains(atlas.toString()) || hook.applyToAtlases.size() == 0) {
                                    Task afterResizeHook = hook.task(atlas.toString(), resolution.toString())

                                    afterResizeHook.mustRunAfter resizeImagesTask
                                    createPacks.dependsOn afterResizeHook
                                }
                        }

                        dependsOn "${name}CreatePacks${resolution}${atlas}"
                        //project.cleanPacks.dependsOn "cleanCreatePacks${resolution}${atlas}"
                        //project.cleanPacks.dependsOn "cleanResizeImages${resolution}${atlas}"
                }
        }
    }
    
    
}
